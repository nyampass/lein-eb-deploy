(ns leiningen.eb-deploy.aws
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    java.text.SimpleDateFormat
    java.util.Date
    [java.util.logging Logger Level]
    com.amazonaws.auth.BasicAWSCredentials
    com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
    com.amazonaws.services.elasticbeanstalk.model.ConfigurationOptionSetting
    com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationRequest
    com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest
    com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.S3Location
    com.amazonaws.services.elasticbeanstalk.model.TerminateEnvironmentRequest
    com.amazonaws.services.s3.AmazonS3Client
    com.amazonaws.regions.Region
    com.amazonaws.regions.Regions))

(defn quiet-logger
  "Stop the extremely verbose AWS logger from logging so many messages."
  []
  (. (Logger/getLogger "com.amazonaws")
     (setLevel Level/WARNING)))

(defn- find-credentials
  [project]
  ((juxt :access-key :secret-key) (:aws project)))

(defn credentials [project]
  (let [[access-key secret-key] (find-credentials project)]
    (if access-key
      (BasicAWSCredentials. access-key secret-key)
      (throw (IllegalStateException.
              "No credentials found; please add to ~/.lein/profiles.clj")))))

(defonce current-timestamp
  (.format (SimpleDateFormat. "yyyyMMddHHmmss") (Date.)))

(defn app-name [project]
  (or (-> project :eb-deploy :app-name)
      (:name project)))

(defn app-version [project]
  (str (:version project) "-" current-timestamp))

(defn s3-bucket-name [project]
  (str "eb." (app-name project)))

(defn project-endpoint [project endpoints]
  (-> project :eb-deploy :region keyword endpoints))

(defn region [name]
  (-> name
      Regions/fromName
      Region/getRegion))

(defn create-bucket [client bucket]
  (when-not (.doesBucketExist client bucket)
    (.createBucket client bucket)))

(defn s3-upload-file [project filename filepath]
  (let [bucket (s3-bucket-name project)
        file (io/file filepath)]
    (doto (AmazonS3Client.)
      (.setRegion (-> project :eb-deploy :region region))
      (create-bucket bucket)
      (.putObject bucket filename file))
    (println "Uploaded" (.getName file) "to S3 bucket")))

(defn- beanstalk-client [project]
  (doto (AWSElasticBeanstalkClient. (credentials project))
    (.setRegion (-> project :eb-deploy :region region))))

(defn create-app-version
  [project filename]
  (.createApplicationVersion
    (beanstalk-client project)
    (doto (CreateApplicationVersionRequest.)
      (.setAutoCreateApplication true)
      (.setApplicationName (app-name project))
      (.setVersionLabel (app-version project))
      (.setDescription (:description project))
      (.setSourceBundle (S3Location. (s3-bucket-name project) filename))))
  (println "Created new app version" (app-version project)))

(defn delete-app-version
  [project version]
  (.deleteApplicationVersion
    (beanstalk-client project)
    (doto (DeleteApplicationVersionRequest.)
      (.setApplicationName (app-name project))
      (.setVersionLabel version)
      (.setDeleteSourceBundle true))))

(defn find-one [pred coll]
  (first (filter pred coll)))

(defn get-application
  "Returns the application matching the passed in name"
  [project]
  (->> (beanstalk-client project)
       .describeApplications
       .getApplications
       (find-one #(= (.getApplicationName %) (app-name project)))))

(defn default-env-vars
  "A map of default environment variables."
  [project]
  (let [[access-key secret-key] (find-credentials project)]
    {"AWS_ACCESS_KEY_ID" access-key
     "AWS_SECRET_KEY" secret-key}))

(defn env-var-options [project options]
  (for [[key value] (merge (default-env-vars project)
                           (:env options))]
    (ConfigurationOptionSetting.
     "aws:elasticbeanstalk:application:environment"
     (if (keyword? key)
       (-> key name str/upper-case (str/replace "-" "_"))
       key)
     value)))

(defn create-environment [project env]
  (println (str "Creating '" (:name env) "' environment")
           "(this may take several minutes)")
  (.createEnvironment
    (beanstalk-client project)
    (doto (CreateEnvironmentRequest.)
      (.setApplicationName (app-name project))
      (.setEnvironmentName (:name env))
      (.setVersionLabel   (app-version project))
      (.setOptionSettings (env-var-options project env))
      (.setCNAMEPrefix (:cname-prefix env))
      (.setSolutionStackName (-> project :eb-deploy :stack-name)))))

(defn update-environment-settings [project env options]
  (.updateEnvironment
    (beanstalk-client project)
    (doto (UpdateEnvironmentRequest.)
      (.setEnvironmentId (.getEnvironmentId env))
      (.setEnvironmentName (.getEnvironmentName env))
      (.setOptionSettings (env-var-options project options)))))

(defn update-environment-version [project env]
  (.updateEnvironment
    (beanstalk-client project)
    (doto (UpdateEnvironmentRequest.)
      (.setEnvironmentId   (.getEnvironmentId env))
      (.setEnvironmentName (.getEnvironmentName env))
      (.setVersionLabel   (app-version project)))))

(defn app-environments [project]
  (->> (beanstalk-client project)
      .describeEnvironments
      .getEnvironments
      (filter #(= (.getApplicationName %) (app-name project)))))

(defn ready? [environment]
  (= (.getStatus environment) "Ready"))

(defn terminated? [environment]
  (= (.getStatus environment) "Terminated"))

(defn get-env [project env-name]
  (->> (app-environments project)
       (find-one #(= (.getEnvironmentName %) env-name))))

(defn get-running-env [project env-name]
  (->> (app-environments project)
       (remove terminated?)
       (find-one #(= (.getEnvironmentName %) env-name))))

(defn poll-until
  "Poll a function until its value matches a predicate."
  ([pred poll]
     (poll-until pred poll 3000))
  ([pred poll & [delay]]
     (loop []
       (Thread/sleep delay)
       (print ".")
       (.flush *out*)
       (let [value (poll)]
         (if (pred value) value (recur))))))

(defn update-environment [project env {name :name :as options}]
  (println (str "Updating '" name "' environment")
           "(this may take several minutes)")
  (update-environment-settings project env options)
  (poll-until ready? #(get-env project name))
  (update-environment-version project env))

(defn deploy-environment
  [project {name :name :as options}]
  (if-let [env (get-running-env project name)]
    (update-environment project env options)
    (throw (IllegalStateException.
            (str "Not found Environment: " name))))
  (let [env (poll-until ready? #(get-env project name))]
    (println " Done")
    (println "Environment deployed at:" (.getCNAME env))))
