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

   [clojure.java.io :as io]

   ;; Stentor custom components
   [kixi.stentor.core :refer (new-main-routes)]
   [kixi.stentor.api :refer (new-poi-api-routes new-area-api-routes new-maps-api-routes)]
   [kixi.stentor.cljs :refer (new-cljs-routes)]

   ;; Modular reusable components
   [modular.core :as mod]
   modular.protocols
   [modular.http-kit :refer (new-webserver)]
   [modular.bidi :refer (new-bidi-ring-handler-provider)]
   ;; [modular.cljs-builder :refer (new-cljs-builder)]

   [shadow.cljs.build :as cljs]

   [kixi.stentor.database :refer (Database)]

   ;; Accessing the API as a client
   [org.httpkit.client :refer (request) :rename {request http-request}]

   [modular.entrance :refer (new-map-backed-user-registry
                             new-atom-backed-session-store
                             new-session-based-request-authorizer
                             new-http-based-request-authorizer
                             new-composite-disjunctive-request-authorizer)]

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

(defn put-map [map user password & {:keys [latlng zoom poi area]}]
  (println
   @(http-request
     {:method :put
      :url (str "http://localhost:8010/maps/" map)
      :headers {"Accept" "application/edn"}
      :basic-auth [user password]
      :body (pr-str {:latlng latlng :zoom zoom :poi poi :area area})}
     :status)))

(defrecord TestData [user password]
  component/Lifecycle
  (start [this]
    (println "Load test data")
    (put-map "city" user password :latlng [51.505 -0.09] :zoom 13 :poi "rent_arrears_anon" :area "hackney-employment")
    (put-map "tenure" user password :latlng [51.505 -0.09] :zoom 10 :poi nil :area "tenure_oa_hackney")
    (println @(http-request
              {:method :get
               :url "http://localhost:8010/maps"
               :basic-auth [user password]
               :headers {"Accept" "application/edn"}
               }
              (comp slurp :body)))
    this)
  (stop [this] this))

(defn new-test-data [{:keys [user password]}]
  (->TestData user password))

(defrecord AtomBackedDatabase []
  component/Lifecycle
  (start [this] (assoc this :store (atom {})))
  (stop [this] this)
  Database
  (store-map! [this username mapname data]
    (swap! (:store this) assoc-in [username mapname] data))
  (get-map [this username mapname] (get-in @(:store this) [username mapname]))
  (index [this username] (keys (get @(:store this) username))))

(defn new-atom-backed-database []
  (->AtomBackedDatabase))

(defrecord FileBackedDatabase [dir]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  Database
  (store-map! [this username mapname data]
    (assert username)
    (let [f (io/file dir (str username ".edn"))]
      (let [m (if (.exists f)
                (read (java.io.PushbackReader. (io/reader f)))
                {})]
        (spit f (pr-str (assoc m mapname data))))))

  (get-map [this username mapname]
    (assert username)
    (let [f (io/file dir (str username ".edn"))]
      (when (.exists f)
        (get (read (java.io.PushbackReader. (io/reader f))) mapname))))

  (index [this username]
    (assert username)
    (let [f (io/file dir (str username ".edn"))]
      (when (.exists f)
        (keys (read (java.io.PushbackReader. (io/reader f))))))))

(defn new-file-backed-database [dir]
  (assert (.exists dir) (format "dbdir doesn't exist: %s" dir))
  (assert (.isDirectory dir) (format "dbdir exists, but isn't a directory: %s" dir))
  (->FileBackedDatabase dir))

(defrecord ApiAuthorizer []
  component/Lifecycle
  (start [this]
    (let [sessions (get this :http-session-store)
          users (get this :user-registry)]
      (when-not sessions (throw (ex-info "No session store!" {:this this})))
      (when-not users (throw (ex-info "No user registry!" {:this this})))
      (assoc this :authorizer (new-composite-disjunctive-request-authorizer
                               (new-session-based-request-authorizer :http-session-store sessions)
                               (new-http-based-request-authorizer :user-password-authorizer users)))))
  (stop [this] this))

(defn new-api-authorizer []
  (->ApiAuthorizer))

(defn new-system []
  (let [cfg (config)
        dbdir (:dbdir cfg)
        users (or (:user-credentials cfg)
                  {"bob" "password"
                   "alice" "pa$$word"
                   "bruce" "otfrom$!"
                   "fran" "mast0d0nC"})]

    (-> (component/system-map
         :web-server (new-webserver (:web-server cfg))
         :bidi-ring-handler (new-bidi-ring-handler-provider)
         :main-routes (new-main-routes)
         :user-registry (new-map-backed-user-registry users)

         :api-authorizer (new-api-authorizer)
         :http-session-store (new-atom-backed-session-store
                              (or (:session-timeout-in-seconds cfg)
                                  (* 60 10))) ; default to 10 mins

         :poi-api-routes (new-poi-api-routes (get-in cfg [:data-dir :poi]) "/data/geojson-poi/")
         :area-api-routes (new-area-api-routes (get-in cfg [:data-dir :area]) "/data/geojson-area/")
         :maps-api-routes (new-maps-api-routes "/maps")
         :cljs-routes (new-cljs-routes (:cljs-builder cfg))

         :database (if dbdir
                     (new-file-backed-database (io/file dbdir))
                     (new-atom-backed-database))

         :cljs-builder (new-cljs-builder)

         ;; For testing, let's just borrow the credentials of one of our users
         ;; :test-data (new-test-data (zipmap [:user :password] (first (seq users))))
         :test-data (new-test-data {:user "bob" :password (get users "bob")}))

        (mod/system-using {:cljs-routes [:cljs-builder]
                           :test-data [:web-server]
                           :maps-api-routes [:database :api-authorizer]
                           :area-api-routes [:api-authorizer]
                           :poi-api-routes [:api-authorizer]
                           :main-routes [:user-registry :http-session-store]
                           :api-authorizer [:http-session-store :user-registry]
                           }))))
