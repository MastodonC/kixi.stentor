(ns kixi.stentor.cljs
  (:require
   [bidi.bidi :refer (->Files)]
   [modular.bidi :refer (new-bidi-routes)])
  )

(defn make-routes [config]
  (let [output-dir (str (:output-dir config) "out/")]
    (println "output dir" output-dir)
    ["/cljs/" (->Files {:dir output-dir
                        :mime-types {"map" "application/javascript"}})]))

(defn new-cljs-routes [config]
  (println "the config is " config)
  (new-bidi-routes (make-routes config) ""))
