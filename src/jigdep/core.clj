(ns jigdep.core
  (:require
   [com.stuartsierra.component :as component]))

(defprotocol Menuitem
  (attributes [_]))

(defrecord Database []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
)

(defn new-database []
  (new Database))

(defrecord Menu []
  component/Lifecycle
  (start [this]
    (println "menuitems is " (:menuitems this))
    (update-in this [:menuitems]
               (partial map attributes))
    )
  (stop [this]
    (println "Stopping menu")
    this))

(defn new-menu []
  (new Menu)
)

(defrecord AboutMenuitem [label]
  component/Lifecycle
  (start [this]
    (println "Starting menuitem")
    this)
  (stop [this]
    (println "Stopping menuitem")
    this)
  Menuitem
  (attributes [this]
    {:label (str label "...")}
    )
  )

(defn resolve-contributors [m k p]
  (reduce-kv
   (fn [s _ v]
     (if (satisfies? p v)
       (update-in s [k] conj v)
       s
       ))
   m m))

(defn configure [m config]
  (reduce-kv
   (fn [s k v]
     (let [cfg (k config)]
       (cond-> s cfg (assoc-in [k :config] cfg))))
   m m))

;; Above this line, no coupling
;; ---------------------------------------------------------

(defn system []
  (-> (component/system-map
       :menu (new-menu)
       :about (new AboutMenuitem "About")
       :about2 (new AboutMenuitem "About2")
       :database (new-database))
      (configure {:database {:keyspace "test"}})
      (resolve-contributors :menuitems Menuitem)
      (component/system-using
       {:menu [:menuitems :database]})))

(prn (-> (component/start (system))))
