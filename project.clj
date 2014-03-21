;; Copyright © 2014, Mastodon C Ltd. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(defproject kixi/stentor "0.1.0-SNAPSHOT"

  :description "Stentor is the herald of the Greeks"

  :url "http://github.com/mastodonc/kixi.stentor"

  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :plugins [[lein-cljsbuild "1.0.2"]]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [juxt/modular "0.2.0"]
                 ;; EDN reader with location metadata
                 [org.clojure/tools.reader "0.8.3"]
                 [juxt.modular/http-kit "0.2.0"]
                 [juxt.modular/bidi "0.2.0"]
                 ;; [juxt/modular.cljs-builder "0.1.0-SNAPSHOT"]
                 [juxt.modular/entrance "0.2.2"]

                 ;; AJAX
                 [cljs-ajax "0.2.3"]

                 ;; core.async
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]

                 ;; Om Debugging
                 [ankha "0.1.1"]

                 ;; Hiccup for templating
                 [hiccup "1.0.5"]

                 ;; temp
                 ;; anything dependency below this line should be removed
                 ;;[org.clojure/java.classpath "0.2.1"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [sablono "0.2.6" :exclusions [com.facebook/react]]
                 [om "0.5.1"]

                 ;; Liberator
                 [liberator "0.11.0"]
                 [cheshire "5.3.1"] ; cheshire for json
                 [org.clojure/data.csv "0.1.2"]

                 [thheller/shadow-build "0.5.0" :exclusions [org.clojure/clojurescript]]
                 ]

  :source-paths ["src" "src-cljs"]

  ;; Keep this while we are stlil using lein cljsbuild auto
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src-cljs"]
              :compiler {:output-to "out/main.js"
                         :output-dir "out"
                         :externs ["om/externs/react.js" "lib/topojson.js"]
                         :optimizations :none
                         :source-map true}}]}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}
             :uberjar {:main kixi.stentor.main
                       :aot [kixi.stentor.main]}})
