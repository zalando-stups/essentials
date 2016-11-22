(defproject org.zalando.stups/essentials "0.12.0-SNAPSHOT"
            :description "Microservice for resource types and scopes."
            :url "https://github.com/zalando-stups/essentials"

            :license {:name "The Apache License, Version 2.0"
                      :url  "http://www.apache.org/licenses/LICENSE-2.0"}

            :min-lein-version "2.0.0"

            :dependencies [[org.clojure/clojure "1.8.0"]
                           [org.zalando.stups/friboo "1.9.0"]
                           [clj-http "2.1.0"]
                           [yesql "0.5.2"]]

            :main ^:skip-aot org.zalando.stups.essentials.core
            :uberjar-name "essentials.jar"

            :plugins [[io.sarnowski/lein-docker "1.1.0"]
                      [org.zalando.stups/lein-scm-source "0.2.0"]
                      [lein-midje "3.1.3"]]

            :docker {:image-name #=(eval (str (some-> (System/getenv "DEFAULT_DOCKER_REGISTRY")
                                                      (str "/"))
                                              "stups/essentials"))}

            :release-tasks [["vcs" "assert-committed"]
                            ["change" "version" "leiningen.release/bump-version" "release"]
                            ["vcs" "commit"]
                            ["vcs" "tag"]
                            ["clean"]
                            ["uberjar"]
                            ["scm-source"]
                            ["docker" "build"]
                            ["docker" "push"]
                            ["change" "version" "leiningen.release/bump-version"]
                            ["vcs" "commit"]
                            ["vcs" "push"]]

            :profiles {:uberjar {:aot :all}

                       :dev     {:repl-options {:init-ns user}
                                 :source-paths ["dev"]
                                 :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                                [org.clojure/java.classpath "0.2.3"]
                                                [midje "1.8.3"]]}})
