# lein-eb-deploy

Leiningen plugin for Amazon's [Elastic Beanstalk][1].

Forked from [lein-beanstalk][4]

## Prerequisites

You will need an [Amazon Web Services][2] account, and know your
account key and secret key.

You will also need to be signed up for Elastic Beanstalk.

## Basic Configuration

To use lein-eb-deploy, you'll need to add a few additional values to
your `project.clj` file.

First, add lein-eb-deploy as a plugin:
```clojure
:plugins [[lein-eb-deploy "0.2.7"]]
```

or, if you're using a version of Leiningen prior to 1.7.0, add it to
your `:dev-dependencies`:
```clojure
:dev-dependencies [[lein-eb-deploy "0.2.7"]]
```
Then add the credentials to your
`~/.lein/profiles.clj` file:
```clojure
{:user
 {:aws {:access-key "XXXXXXXXXXXXXXXXXX"
        :secret-key "YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"}}}
```
Finally, lein-eb-deploy uses lein-ring for packaging your
application, so all of lein-ring's configuration applies as well.
At a minimum, you'll need to your `project.clj` a reference to
your application's top-level handler, e.g.:

```clojure
:ring {:handler hello-world.core/handler}
```

See the documentation for [lein-ring](https://github.com/weavejester/lein-ring)
for more about the options it provides.

### Deploy

You should now be able to deploy your application to the Amazon cloud
using the following command:

    $ lein eb-deploy deploy development

### Info

To get information about the application itself run

    $ lein eb-deploy info
    Application Name : myapp
    Description      : My Awesome Compojure App
    Last 5 Versions  : 0.1.0-20110209030504
                       0.1.0-20110209030031
                       0.1.0-20110209025533
                       0.1.0-20110209021110
                       0.1.0-20110209015216
    Created On       : Wed Feb 09 03:00:45 EST 2011
    Updated On       : Wed Feb 09 03:00:45 EST 2011
    Deployed Envs    : development (Ready)
                       staging (Ready)
                       production (Terminated)

and information about a particular environment execute

    $ lein eb-deploy info development
    Environment Id   : e-lm32mpkr6t
    Application Name : myapp
    Environment Name : development
    Description      : Default environment for the myapp application.
    URL              : development-feihvibqb.elasticbeanstalk.com
    LoadBalancer URL : awseb-myapp-46156215.us-east-1.elb.amazonaws.com
    Status           : Ready
    Health           : Green
    Current Version  : 0.1.0-20110209030504
    Solution Stack   : 32bit Amazon Linux running Tomcat 6
    Created On       : Tue Feb 08 08:01:44 EST 2011
    Updated On       : Tue Feb 08 08:05:01 EST 2011

### Shutdown

To shutdown an existing environment use the following command

    $ lein eb-deploy terminate development

This terminates the environment and all of its resources, i.e.
the Auto Scaling group, LoadBalancer, etc.

### Cleanup

To remove any unused versions from the S3 bucket run

    $ lein eb-deploy clean


##  Configuration

### AWS Credentials

The [Amazon Web Services][2] account key and secret key should be
put into a `lein-beanstalk-credentials` definition in your
`~/.lein/profiles.clj` file:
```clojure
{:user {:aws {:access-key "XXXXXXXXXXXXXXXXXX"
              :secret-key "YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"})
```

Keeping your credentials out of your `project.clj` file and out
of your project in general helps ensure you don't accidentally
commit your credentials to github et al.

However, If you want to deploy your application using beanstalk from
an environment like Jenkins, where you don't have control over the
user, you can export the credential to the environment from the build
script, and inside your `project.clj`, do:
```clojure
(defproject my-project "0.1.0"
    :description ...
    :aws {
        :access-key ~(System/getenv "AWS_ACCESS_KEY")
        :secret-key ~(System/getenv "AWS_SECRET_KEY")})
```
### Environments

Elastic Beanstalk environments can be defined in multiple ways in
the `project.clj` file.

If no environments are specified, lein-eb-deploy will create three
default environments

* `development` (with CNAME prefix `myapp-development`)
* `staging` (with CNAME prefix `myapp-staging`)
* `production` (with CNAME prefix `myapp`)

To override the default behavior, add an `:aws` key to your
`project.clj` file, either with `:environments` mapped to a
vector of envionment symbols:
```clojure
{:eb-deploy {:environments [dev demo prod]
             ...}
      ...}
```

or to a vector of maps
```clojure
:eb-deploy {:environments [{:name "dev"}
                            {:name "demo"}
                            {:name "prod"}]
             ...}
```
Given either of the above configurations, the following two
environents will be created:

* `dev` (with CNAME prefix `myapp-dev`)
* `demo` (with CNAME prefix `myapp-demo`)
* `prod` (with CNAME prefix `myapp-prod`)

The second option allows one to specify the CNAME prefix for each
environment
```clojure
:eb-deploy {:environments [{:key "dev"
                            :name "app-dev"
                            :cname-prefix "myapp-development"}
                           {:key "staging"
                            :name "app-staging"
                            :cname-prefix "myapp-demo"}
                           {:key "prod"
                            :name "app"
                            :cname-prefix "myapp"}]
            ...}
```
By default the CNAME prefix is `<project-name>-<environment>`.

### Environment Variables

You can specify environment variables that will be added to the system
properties of the running application, per beanstalk environment:
```clojure
:eb-deploy
 {:environments
  [{:name "dev"
    :cname-prefix "myapp-dev"
    :env {"DATABASE_URL" "mysql://..."}}]}
```

If the environment variable name is a keyword, it is upper-cased and
underscores ("_") are substituted for dashes ("-"). e.g.
`:database-url` becomes `"DATABASE_URL"`.

### S3 Buckets

[Amazon Elastic Beanstalk][1] uses
[Amazon Simple Storage Service (S3)][3] to store the versions
of the application. By default lein-eb-deploy uses
`eb.<project-name>` as the S3 bucket name.

To use a custom bucket, specify it in the `project.clj` file:
```clojure
:eb-deploy {:s3-bucket "my-private-bucket"
            ...}
```
### Regions

You can specify the AWS region of to deploy the application to through
your `project.clj` file:
```clojure
:aws {:eb-deploy {:region "eu-west-1"}}
```

[AWS Regions and Endpoints][5]

[1]: http://aws.amazon.com/elasticbeanstalk
[2]: http://aws.amazon.com
[3]: http://aws.amazon.com/s3
[4]: https://github.com/weavejester/lein-beanstalk
[5]: https://docs.aws.amazon.com/general/latest/gr/rande.html
