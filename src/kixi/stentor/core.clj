(ns kixi.stentor.core
  (:require
   [modular.http-kit :refer (new-webserver)]
   [modular.bidi :refer (new-bidi-routes new-bidi-ring-handler-provider)]
   [bidi.bidi :as bidi]
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
    {:label (str label "...")}))

(defn index [handlers-p]
  (fn [req]
    {:status 200 :body "Hello, this is the index!"}))

(defn make-handlers []
  (let [p (promise)]
    @(deliver p
              {:index (index p)})))

(defn make-routes [handlers]
  ["/index.html" (:index handlers)])

(defn new-main-routes []
  (new-bidi-routes (make-routes (make-handlers)) ""))

(defn new-sub-routes []
  (new-bidi-routes (make-routes (make-handlers)) "/bar/foo"))
