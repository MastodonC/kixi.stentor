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

(ns kixi.stentor.system
  (:require
   [com.stuartsierra.component :as component]

   ;; Stentor custom components
   [kixi.stentor.core :refer (new-main-routes)]
   [kixi.stentor.api :refer (new-api-routes)]
   [kixi.stentor.cljs :refer (new-cljs-routes)]

   ;; Modular reusable components
   [modular.core :as mod]
   [modular.http-kit :refer (new-webserver)]
   [modular.bidi :refer (new-bidi-ring-handler-provider)]
   ;; [modular.cljs-builder :refer (new-cljs-builder)]

   [shadow.cljs.build :as cljs]

   ;; Misc
   clojure.tools.reader
   [clojure.tools.reader.reader-types :refer (indexing-push-back-reader source-logging-push-back-reader)]
   [clojure.java.io :as io]))

(defn config []
  (let [f (io/file (System/getProperty "user.home") ".stentor.edn")]
    (when (.exists f)
      (clojure.tools.reader/read
       (indexing-push-back-reader
        (java.io.PushbackReader. (io/reader f)))))))




(defn define-modules [state]
  (-> state
      (cljs/step-configure-module
       :cljs ;; module name
       ['cljs.core] ;; module mains, a main usually contains exported functions or code that just runs
       #{}) ;; module dependencies
      (cljs/step-configure-module :hecuba ['kixi.stentor.main] #{:cljs})
      ))

(defn message [state message]
  (println message)
  state
)

(defn compile-cljs
  "build the project, wait for file changes, repeat"
  [& args]
  (let [state (-> (cljs/init-state)
                  (cljs/enable-source-maps)
                  (assoc :optimizations :none
                         :pretty-print true
                         :work-dir (io/file "target/cljs-work") ;; temporary output path, not really needed
                         :public-dir (io/file "target/cljs") ;; where should the output go
                         :public-path "/cljs") ;; whats the path the html has to use to get the js?
                  (cljs/step-find-resources-in-jars) ;; finds cljs,js in jars from the classpath
                  (cljs/step-find-resources "lib/js-closure" {:reloadable false})
                  (cljs/step-find-resources "src-cljs") ;; find cljs in this path
                  (cljs/step-finalize-config) ;; shouldn't be needed but is at the moment
                  (cljs/step-compile-core) ;; compile cljs.core
                  (define-modules)
                  )]

    (-> state
        (cljs/step-compile-modules)
        (cljs/flush-unoptimized)))

  :done)

(defrecord ClojureScriptBuilder []
  component/Lifecycle
  (start [this]
    (try
      (compile-cljs)
      this
      (catch Exception e
        (println "ClojureScript build failed:" e)
        (assoc this :error e))))
  (stop [this] this))

(defn new-cljs-builder []
  (->ClojureScriptBuilder))


(defn new-system []
  (let [cfg (config)]
    (-> (component/system-map
         :web-server (new-webserver (:web-server cfg))
         :bidi-ring-handler (new-bidi-ring-handler-provider)
         :main-routes (new-main-routes)
         :api-routes (new-api-routes (:geojson-poi-data cfg))
         :cljs-routes (new-cljs-routes (:cljs-builder cfg))
         :cljs-builder (new-cljs-builder)
         )
        (mod/system-using {:cljs-routes [:cljs-builder]}))))
