(ns kixi.stentor.cljs
  (:require
   [bidi.bidi :refer (->Files)]
   [modular.bidi :refer (new-bidi-routes)])
  )

(defn make-routes [config]
  (let [output-dir (str (:output-dir config) "out/")]
    ["/cljs/" (->Files {:dir output-dir
                        :mime-types {"map" "application/javascript"}})]))

(defn new-cljs-routes [config]
  (new-bidi-routes (make-routes config) ""))
