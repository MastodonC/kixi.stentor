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
   [cheshire.core :as json]))

(defresource index [handlers]
  :available-media-types ["text/html"]
  :handle-ok "OK, I'm an API, how are you?")

(io/resource (str "data/" "bydureon.js"))

(defresource geojson [drug-data-path handlers]
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [drugname]} :route-params} :request}]
             (when-let [res (io/file drug-data-path (str drugname ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [drugname]} :route-params} :request res ::resource}]
               (when res (json/parse-stream (io/reader res)))))

(defn make-api-handlers [drug-data-path]
  (let [p (promise)]
    @(deliver p
              {:index (index p)
               :geojson (geojson drug-data-path p)})))

(defn make-api-routes [handlers]
  ["/"
   [["index" (:index handlers)]
    [["geojson/" :drugname] (:geojson handlers)]]])

(defn new-api-routes [drug-data-path]
  (assert drug-data-path "No drug-data-path")
  (assert (.exists (io/file drug-data-path)) (format "Directory doesn't exist: %s" drug-data-path))
  (-> (make-api-handlers drug-data-path)
      make-api-routes
      (new-bidi-routes "/data")))
