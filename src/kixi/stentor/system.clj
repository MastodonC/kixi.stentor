(ns kixi.stentor.system
  (:require
   [kixi.stentor.core :refer (new-menu new-database ->AboutMenuitem Menuitem)]
   [com.stuartsierra.component :as component]
   [modular.core :as mod]
   [clojure.tools.reader.reader-types :refer (indexing-push-back-reader source-logging-push-back-reader)]
   [clojure.java.io :as io]))

(defn config []
  (let [f (io/file (System/getProperty "user.home") ".stentor.edn")]
    (when (.exists f)
      (clojure.tools.reader/read
       (indexing-push-back-reader
        (java.io.PushbackReader. (io/reader f)))))))

(defn system []
  (-> (component/system-map
       :menu (new-menu)
       :about (->AboutMenuitem "About")
       :about2 (->AboutMenuitem "About2")
       :database (new-database))
      (mod/configure (config))
      (mod/resolve-contributors :menuitems Menuitem)
      (component/system-using
       {:menu [:menuitems :database]})))

(prn (-> (component/start (system))))
