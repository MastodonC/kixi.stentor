(ns kixi.stentor.history
  (:require
   [goog.History :as history]
   [goog.history.Html5History :as history5]))

(defn new-history
  "Create a history object."
  []
  (goog.history.Html5History.))

(def hist (new-history))

(defn set-token! [tok]
  (.setToken hist tok))
