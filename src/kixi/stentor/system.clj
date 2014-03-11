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
   [kixi.stentor.core :refer (new-data-routes new-main-routes new-menu new-database ->AboutMenuitem Menuitem)]
   [kixi.stentor.cljs :refer (new-cljs-routes)]

   ;; Modular reusable components
   [modular.core :as mod]
   [modular.http-kit :refer (new-webserver)]
   [modular.ring :refer (resolve-handler-provider)]
   [modular.bidi :refer (RoutesContributor new-bidi-ring-handler-provider)]
   ;; [modular.cljs-builder :refer (new-cljs-builder)]

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

(defn new-system []
  (let [cfg (config)]
    (-> (component/system-map
         :webserver (new-webserver (:webserver cfg))
         :bidi-ring-handler (new-bidi-ring-handler-provider)
         :main-routes (new-main-routes)
         :data-routes (new-data-routes)
         :cljs-routes  (new-cljs-routes (:cljs-builder cfg))

         ;;:cljs-builder (new-cljs-builder (:cljs-builder cfg))
         :menu (new-menu)
         :about (->AboutMenuitem "About")
         :about2 (->AboutMenuitem "About2")
         :database (new-database))
        (mod/resolve-contributors :menuitems Menuitem)
        (resolve-handler-provider)
        (mod/resolve-contributors :routes-contributors RoutesContributor) ; only temporarily cardinality of 1
        (component/system-using
         {:menu [:menuitems :database]
          :ring-handler-provider [:routes-contributors]}))))

;;(prn (-> (component/start (system))))
