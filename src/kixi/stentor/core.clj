(ns kixi.stentor.core
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
    (update-in this [:menuitems]
               (partial map attributes))
    )
  (stop [this]
    this))

(defn new-menu []
  (new Menu)
)

(defrecord AboutMenuitem [label]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)
  Menuitem
  (attributes [this]
    {:label (str label "...")}
    )
  )



;; Above this line, no coupling
;; ---------------------------------------------------------
