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
   [modular.bidi :refer (new-bidi-routes BidiRoutesContributor)]
   [bidi.bidi :refer (->WrapMiddleware)]
   [liberator.core :refer (defresource)]
   [cheshire.core :as json]
   [ring.middleware.cookies :refer (wrap-cookies)]
   [kixi.stentor.colorbrewer :as color]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [kixi.stentor.database :refer (store-map! get-map index)]
   [modular.entrance
    :refer (new-session-based-request-authorizer
            new-http-based-request-authorizer
            authorized-request?
            new-composite-disjunctive-request-authorizer)]))

;; Bucketing
(defn bucket [v min max steps]
  ;; (println (format "v: %s min: %s max: %s steps:%s scheme: %s" v min max steps scheme))
  (let [step    (/ (- max min) steps)
        buckets (range min max step)]
    (dec (count (remove #(< v %) buckets)))))

(defn buckets [geojson]
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

;; POI

(defresource poi-data [dir authorizer handlers]
  :authorized? (fn [{request :request}] (authorized-request? authorizer request))
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [path]} :route-params} :request}]
             (when-let [res (io/file dir (str path ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [path]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)
                     buckets))))

(defn make-poi-api-handlers [dir authorizer]
  (let [p (promise)]
    @(deliver p
              {:data (poi-data dir authorizer p)})))

(defn make-poi-api-routes [handlers]
  [""
   [[[:path] (:data handlers)]]])

(defrecord PoiApiRoutes [dir context]
  component/Lifecycle
  (start [this]
    (let [authorizer (get-in this [:api-authorizer :authorizer])]
      (when-not authorizer (throw (ex-info "No authorizer!" {:this this})))
      (assoc this :routes ["" (->WrapMiddleware
                               [(make-poi-api-routes (make-poi-api-handlers dir authorizer))]
                               wrap-cookies)])))
  (stop [this] this)

  BidiRoutesContributor
  (routes [this] (:routes this))
  (context [this] context))

(defn new-poi-api-routes [dir context]
  (assert dir "No data dir")
  (assert (.exists (io/file dir)) (format "Directory doesn't exist: %s" dir))
  (->PoiApiRoutes dir context))

;; Area

(defresource area-data [dir authorizer handlers]
  :authorized? (fn [{request :request}]
                 (authorized-request? authorizer request))
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [path]} :route-params} :request}]
             (when-let [res (io/file dir (str path ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [path]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)
                     buckets))))

(defn make-area-api-handlers [dir authorizer]
  (let [p (promise)]
    @(deliver p
              {:data (area-data dir authorizer p)})))

(defn make-area-api-routes [handlers]
  [""
   [[[:path] (:data handlers)]]])

(defrecord AreaApiRoutes [dir context]
  component/Lifecycle
  (start [this]
    (let [authorizer (get-in this [:api-authorizer :authorizer])]
      (when-not authorizer (throw (ex-info "No authorizer!" {:this this})))
      (assoc this :routes ["" (->WrapMiddleware
                               [(make-area-api-routes (make-area-api-handlers dir authorizer))]
                               wrap-cookies)])))
  (stop [this] this)

  BidiRoutesContributor
  (routes [this] (:routes this))
  (context [this] context))

(defn new-area-api-routes [dir context]
  (assert dir "No data dir")
  (assert (.exists (io/file dir)) (format "Directory doesn't exist: %s" dir))
  (->AreaApiRoutes dir context))

;; Load/Save maps

(defresource maps-index [handlers database authorizer]
  :authorized? (fn [{request :request}] {:auth-request (authorized-request? authorizer request)})
  :available-media-types ["application/edn"]
  :handle-ok (fn [{{username :username} :auth-request}]
               (assert username)
               (for [map (index database username)]
                 (assoc (get-map database username map) :map map))))

(defresource maps-item [handlers database authorizer]
  :authorized? (fn [{request :request}] {:auth-request (authorized-request? authorizer request)})
  :allowed-methods #{:get :put}
  :available-media-types ["application/edn"]
  :handle-ok "Here's a map"
  :exists? true
  :put! (fn [{{{:keys [map]} :route-params body :body} :request {username :username} :auth-request}]
          (assert username)
          (let [body (slurp body)]
            (let [data (edn/read-string body)]
              (store-map! database username map data)))))

(defn make-maps-api-handlers [database authorizer]
  (let [p (promise)]
    @(deliver p
              {:index (maps-index p database authorizer)
               :map (maps-item p database authorizer)})))

(defn make-maps-api-routes [handlers]
  ["" [["" (:index handlers)]
       [["/" :map] (:map handlers)]]])

(defrecord MainRoutes [context]
  component/Lifecycle
  (start [this]
    (let [database (get-in this [:database])
          authorizer (get-in this [:api-authorizer :authorizer])]
      (when-not database (throw (ex-info "No database!" {:this this})))
      (when-not authorizer (throw (ex-info "No authorizer!" {:this this})))
      (assoc this
        :routes ["" (->WrapMiddleware
                     [(make-maps-api-routes (make-maps-api-handlers database authorizer))]
                     wrap-cookies)])))
  (stop [this] this)

  BidiRoutesContributor
  (routes [this] (:routes this))
  (context [this] context))

(defn new-maps-api-routes [context]
  (->MainRoutes context))
