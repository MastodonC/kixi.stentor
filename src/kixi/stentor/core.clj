(ns kixi.stentor.core
  (:require
   [modular.http-kit :refer (->Webserver)]
   modular.bidi
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

(defn index [handlers-p]
  (fn [req]
    {:status 200 :body "Hello, this is the index!"}))

(defn make-handlers []
  (let [p (promise)]
    @(deliver p
              {:index (index p)})))

(defn make-routes [handlers]
  ["/index.html" (:index handlers)])

(defrecord BidiRoutes []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  modular.bidi/RoutesContributor
  (routes [this] (make-routes (make-handlers))))

(defn wrap-routes
  "Add the final set of routes from which the Ring handler is built."
  [h routes]
  (fn [req]
    (h (assoc req :routes routes))))

(defrecord BidiRingHandlerProvider []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  modular.http-kit/RingHandlerProvider
  (handler [this]
    (assert (:routes this) "No :routes found")
    (let [routes (modular.bidi/routes (:routes this))]
      (-> routes
       bidi/make-handler
       (wrap-routes routes)))))

(defn new-bidi-ring-handler-provider []
  (new BidiRingHandlerProvider))

(defn new-main-routes []
  (new BidiRoutes))
