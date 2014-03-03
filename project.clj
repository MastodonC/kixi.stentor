(defproject kixi/stentor "0.1.0-SNAPSHOT"

  :description "Stentor is the herald of the Greeks"

  :url "http://github.com/mastodonc/kixi.stentor"

  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [juxt/modular "0.1.0-SNAPSHOT"]
                 ;; EDN reader with location metadata
                 [org.clojure/tools.reader "0.8.3"]
                 [juxt/modular.http-kit "0.1.0-SNAPSHOT"]
                 [juxt/modular.bidi "0.1.0-SNAPSHOT"]
                 ]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}
             :uberjar {:main kixi.stentor.main
                       :aot [kixi.stentor.main]}})
