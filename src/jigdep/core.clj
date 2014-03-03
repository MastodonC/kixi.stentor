(ns jigdep.core
  (:require
   [com.stuartsierra.component :as component]))

(defprotocol Menuitem
  (attributes [_]))

(defrecord Database []
  component/Lifecycle
  (start [this]
    this)
  (stop [this] this)
)

(defn new-database []
  (new Database))

(defrecord Menu []
  component/Lifecycle
  (start [this]
    (println "Starting menu: " (vals this))
    (reduce-kv
     (fn [s k v]
       (if (satisfies? Menuitem v)
         (update-in s [:menuitems] conj (attributes v))
         s))
     this this))
  (stop [this]
    (println "Stopping menu")
    this))

(defn new-menu []
  (new Menu)
)

(defrecord AboutMenuitem []
  component/Lifecycle
  (start [this]
    (println "Starting menuitem")
    this)
  (stop [this]
    (println "Stopping menuitem")
    this)
  Menuitem
  (attributes [this]
    {:label "About..."}
    )
  )

(defn new-menuitem []
  (new AboutMenuitem)
)

;; Above this line, no coupling
;; ---------------------------------------------------------

(defn new-system []
  (-> (component/system-map
       :menu (new-menu)
       :about (new-menuitem)
       :database (new-database)
       )
      (component/system-using
       {:menu [:about :database]})))


(println (-> (component/start (new-system)) :menu :menuitems))
