(ns user
  (:require
   [com.stuartsierra.component :as component]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [kixi.stentor.system :as stentor]
   modular))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'modular/system
    (constantly (stentor/new-system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'modular/system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'modular/system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
