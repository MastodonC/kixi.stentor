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

;; POI

(defresource poi-data [dir handlers]
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [path]} :route-params} :request}]
             (println "dir is" dir)
             (println "path is" path)
             (when-let [res (io/file dir (str path ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [path]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)))))

(defn make-poi-api-handlers [dir]
  (let [p (promise)]
    @(deliver p
              {:data (poi-data dir p)})))

(defn make-poi-api-routes [handlers]
  [""
   [[[:path] (:data handlers)]]])

(defn new-poi-api-routes [dir context]
  (assert dir "No data dir")
  (assert (.exists (io/file dir)) (format "Directory doesn't exist: %s" dir))
  (-> (make-poi-api-handlers dir)
      make-poi-api-routes
      (new-bidi-routes :context context)))

;; Area

(defn bucket [v min max steps]
  ;; (println (format "v: %s min: %s max: %s steps:%s scheme: %s" v min max steps scheme))
  (let [step    (/ (- max min) steps)
        buckets (range min max step)]
    (dec (count (remove #(< v %) buckets)))))

(defn buckets [geojson]
  (println "geojson is" geojson)
  (let [features (remove #(nil? (get-in % [:properties :v])) (:features geojson))
        vs       (map #(get-in % [:properties :v]) features)
        min-val  (reduce #(min %1 %2) vs)
        max-val  (reduce #(max %1 %2) vs)
        steps    7]
    ;; (println (format "min: %s max: %s features: %s" min-val max-val (count features)))
    {:type "FeatureCollection"
     :min min-val
     :max max-val
     :features
     (mapv (fn [feature]
             (update-in feature [:properties]
                        assoc :bucket
                        (bucket (get-in feature [:properties :v])
                                min-val max-val steps)))
           features)}))

(defresource area-data [dir handlers]
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [path]} :route-params} :request}]
             (println "dir is" dir)
             (println "path is" path)
             (when-let [res (io/file dir (str path ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [path]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)
                     buckets))))

(defn make-area-api-handlers [dir]
  (let [p (promise)]
    @(deliver p
              {:data (area-data dir p)})))

(defn make-area-api-routes [handlers]
  [""
   [[[:path] (:data handlers)]]])

(defn new-area-api-routes [dir context]
  (assert dir "No data dir")
  (assert (.exists (io/file dir)) (format "Directory doesn't exist: %s" dir))
  (-> (make-area-api-handlers dir)
      make-area-api-routes
      (new-bidi-routes :context context)))
