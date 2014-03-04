(ns kixi.stentor.cljs
  (:require
   [bidi.bidi :refer (->Files)]
   [modular.bidi :refer (new-bidi-routes)])
  )

(defn make-routes [config]
  (println "type is " (type (:output-dir config)))
  ["/cljs/" (->Files {:dir (:output-dir config)
                      :mime-types {"map" "application/javascript"}})])

(defn new-cljs-routes [config]
  (println "the config is " config)
  (new-bidi-routes (make-routes config) ""))
