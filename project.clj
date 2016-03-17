(defproject lein-eb-deploy "0.1"
  :description "Leiningen plugin for deploy Amazon's Elastic Beanstalk Application"
  :url "https://github.com/nyampass/lein-eb-deploy"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.amazonaws/aws-java-sdk "1.10.61"]
                 [lein-ring "0.9.7"]]
  :eval-in-leiningen true)
