(ns kixi.stentor.cljs
  (:require
   [bidi.bidi :refer (->Files)]
   [modular.bidi :refer (new-bidi-routes)])
  )

(defn make-routes [config]
  (let [output-dir (str (:output-dir config) "out/")]
    ["" (->Files {:dir "target/cljs"
                  :mime-types {"map" "application/javascript"}})]))

(defn new-cljs-routes [config]
  (new-bidi-routes (make-routes config) :context "/cljs/"))
