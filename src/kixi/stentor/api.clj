;; Copyright © 2014, Mastodon C Ltd. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns kixi.stentor.api
  (:require
   [clojure.java.io :as io]
   [modular.bidi :refer (new-bidi-routes)]
   [liberator.core :refer (defresource)]
   [cheshire.core :as json]
   [kixi.stentor.colorbrewer :as color]))

(defresource index [handlers]
  :available-media-types ["text/html"]
  :handle-ok "OK, I'm an API, how are you?")

(io/resource (str "data/" "bydureon.js"))

(defn colored-map [geojson]
  (let [features (remove #(nil? (get-in % [:properties :v])) (:features geojson))
        vs       (map #(get-in % [:properties :v]) features)
        min-val  (reduce #(min %1 %2) vs)
        max-val  (reduce #(max %1 %2) vs)
        steps    7
        scheme   :Greens]
    ;; (println (format "min: %s max: %s features: %s" min-val max-val (count features)))
    {:type "FeatureCollection"
     :features
     (mapv (fn [feature]
             (update-in feature [:properties]
                        assoc :color
                        (color/brewer (get-in feature [:properties :v])
                                      min-val max-val steps scheme)))
           features)}))

(defresource geojson-poi [poi-path handlers]
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [poi]} :route-params} :request}]
             (when-let [res (io/file poi-path (str poi ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [poi]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)
                     colored-map
                     ))))

(defn make-api-handlers [poi-path]
  (let [p (promise)]
    @(deliver p
              {:index (index p)
               :geojson-poi (geojson-poi poi-path p)})))

(defn make-api-routes [handlers]
  ["/"
   [["index" (:index handlers)]
    [["geojson-poi/" :poi] (:geojson-poi handlers)]]])

(defn new-api-routes [poi-path]
  (assert poi-path "No poi-path")
  (assert (.exists (io/file poi-path)) (format "Directory doesn't exist: %s" poi-path))
  (-> (make-api-handlers poi-path)
      make-api-routes
      (new-bidi-routes "/data")))
