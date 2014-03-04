(defproject kixi/stentor "0.1.0-SNAPSHOT"

  :description "Stentor is the herald of the Greeks"

  :url "http://github.com/mastodonc/kixi.stentor"

  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :plugins [[lein-cljsbuild "1.0.2"]]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [juxt/modular "0.1.0-SNAPSHOT"]
                 ;; EDN reader with location metadata
                 [org.clojure/tools.reader "0.8.3"]
                 [juxt/modular.http-kit "0.1.0-SNAPSHOT"]
                 [juxt/modular.bidi "0.1.0-SNAPSHOT"]
                 [juxt/modular.cljs-builder "0.1.0-SNAPSHOT"]


                 ;; temp
                 ;; anything dependency below this line should be removed
                 ;;[org.clojure/java.classpath "0.2.1"]
                 [org.clojure/clojurescript "0.0-2173"]
                 ]

  :source-paths ["src" "src-cljs"]

  ;; Keep this while we are stlil using lein cljsbuild auto
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src-cljs"]
              :compiler {
                :output-to "main.js"
                :output-dir "target/js"
                :optimizations :none
                :source-map true}}]}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}
             :uberjar {:main kixi.stentor.main
                       :aot [kixi.stentor.main]}})
