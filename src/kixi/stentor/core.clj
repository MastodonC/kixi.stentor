(ns kixi.stentor.core
  (:require
   [modular.http-kit :refer (->Webserver)]
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

(defrecord Hello []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  modular.http-kit/RingHandlerProvider
  (handler [this] (fn [req] {:status 200 :body "Hello Neale!!!!!!!!!"})))

(defn new-hello []
  (new Hello))

(defn new-webserver [{:keys [port]}]
  (->Webserver port))



;; Above this line, no coupling
;; ---------------------------------------------------------
