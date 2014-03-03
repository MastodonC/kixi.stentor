(ns kixi.stentor.system
  (:require
   [kixi.stentor.core :refer (new-sub-routes new-main-routes new-hello new-menu new-database ->AboutMenuitem Menuitem)]
   [com.stuartsierra.component :as component]
   [modular.core :as mod]
   [modular.http-kit :refer (RingHandlerProvider new-webserver)]
   [modular.bidi :refer (RoutesContributor new-bidi-ring-handler-provider)]
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
;;         :ring-handler (new-hello)
         :bidi-ring-handler (new-bidi-ring-handler-provider)
         :main-routes (new-main-routes)
         :sub-routes (new-sub-routes)
         :menu (new-menu)
         :about (->AboutMenuitem "About")
         :about2 (->AboutMenuitem "About2")
         :database (new-database))
        (mod/resolve-contributors :menuitems Menuitem)
        (mod/resolve-contributors :ring-handler-provider RingHandlerProvider :cardinality 1)
        (mod/resolve-contributors :routes-contributors RoutesContributor) ; only temporarily cardinality of 1
        (component/system-using
         {:menu [:menuitems :database]
          :webserver [:ring-handler-provider]
          :ring-handler-provider [:routes-contributors]}))))

;;(prn (-> (component/start (system))))
