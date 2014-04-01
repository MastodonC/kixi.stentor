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
   [ring.middleware.cookies :refer (cookies-request)]
   [kixi.stentor.colorbrewer :as color]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [cylon.liberator :refer (make-composite-authorizer)]
   [kixi.stentor.database :refer (store-map! get-map index)]))

;; Make it public
(defn dummy-authorizer [context]
  ;;(println "Context: " context)
  true)

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

;; FIXME Evil hack to get session details
(defn session-id->session-details [session system]
  (-> system
      :protection-system
      :http-session-store
      :sessions
      deref
      (get session)))

;; POI

(defresource poi-data [dir authorizer handlers]
  :authorized? dummy-authorizer ;;authorizer
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [path]} :route-params} :request}]
             (when-let [res (io/file dir (str path ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [path]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)
                     buckets))))

(defresource poi-index [component authorizer]
  :authorized? dummy-authorizer
  :available-media-types ["application/edn"]
  :handle-ok (fn [context]
               (let [public-poi [{:label "PUBLIC! Hackney Schools Population (Dummy Data)" :value "schools_hackney"}]
                     session (-> context :request cookies-request :cookies (get "session") :value)
                     session-details (session-id->session-details session component)
                     username (:username session-details)
                     user-poi (-> component :protection-system :password-store :ref deref (get username) :poi)]
                 (if username
                   (concat public-poi user-poi)
                   public-poi))))

(defn make-poi-api-handlers [dir component authorizer]
  (let [p (promise)]
    @(deliver p {:index (poi-index component authorizer)
                 :data (poi-data dir authorizer p)})))

(defn make-poi-api-routes [handlers]
  ["" [["" (:index handlers)]
       [["/" :path] (:data handlers)]]])

(defn new-poi-api-routes [dir context]
  (assert dir "No data dir")
  (assert (.exists (io/file dir)) (format "Directory doesn't exist: %s" dir))
  (component/using
             (new-bidi-routes
              (fn [component]
                (->> component
                     :protection-system
                     make-composite-authorizer
                     (make-poi-api-handlers dir component)
                     make-poi-api-routes))
              :context context)
             [:protection-system]))

;; Areas
;; FIXME This should come from an EDN file or other database
(defn public-area []
  [;; Bexley
   {:label "Bexley Percent Unemployed" :value "bexley_employment"}
   {:label "Bexley National Insurance Registrations" :value "bexley_nino"}
   {:label "Bexley Broadband Speed" :value "bexley_broadband_speed"}                    

   ;; Accommodation
   {:label "Bexley Percent Over Occupied" :value "bexley_occupancy"}
   {:label "Bexley Percent Social Rented" :value "bexley_tenure"}

   ;; Community
   {:label "Bexley Belonging" :value "bexley_belonging"}
   {:label "Bexley Community Cohesion" :value "bexley_communitycohesion"}
   {:label "Bexley Community Safety" :value "bexley_communitysafety"}
   {:label "Bexley Taking Part" :value "bexley_takingpart"}

   ;; WARM
   {:label "Bexley Resilience (WARM)" :value "bexley_resilience"}
   {:label "Bexley Wellbeing (WARM)" :value "bexley_wellbeing"}

   ;; Crime
   {:label "Bexley Anti-social Behaviour" :value "bexley_crime_scrub_Anti-social_behaviour"}
   {:label "Bexley Burglary" :value "bexley_crime_scrub_Burglary"}
   {:label "Bexley Criminal Damage and Arson" :value "bexley_crime_scrub_Criminal_damage_and_arson"}
   {:label "Bexley Drugs" :value "bexley_crime_scrub_Drugs"}
   {:label "Bexley Other Crime" :value "bexley_crime_scrub_Other_crime"}
   {:label "Bexley Other Theft" :value "bexley_crime_scrub_Other_theft"}
   {:label "Bexley Robbery" :value "bexley_crime_scrub_Robbery"}
   {:label "Bexley Shoplifting" :value "bexley_crime_scrub_Shoplifting"}
   {:label "Bexley Vehicle Crime" :value "bexley_crime_scrub_Vehicle_crime"}
   {:label "Bexley Total Crime" :value "bexley_crime_scrub_Total_crime"}

   ;; Cambridge
   {:label "Cambridge Broadband Speed" :value "cambridge_broadband_speed"}

   {:label "Cambridge Employment" :value "cambridge_employment"}
   {:label "Cambridge National Insurance Registrations" :value "cambridge_nino"}
   {:label "Cambridge Demographics Library Users" :value "cambridge_demographicslibraryusers"}
   {:label "Cambridge Demographics Library Visits" :value "cambridge_demographicslibraryvisits"}

   ;; Accommodation
   {:label "Cambridge Percent Flats (vs Houses)" :value "cambridge_accommodation_type"}
   {:label "Cambridge Percent Over Occupied" :value "cambridge_occupancy"}
   {:label "Cambridge Percent Social Rented" :value "cambridge_tenure"}

   ;; Community
   {:label "Cambridge Belonging" :value "cambridge_belonging"}
   {:label "Cambridge Community Cohesion" :value "cambridge_communitycohesion"}
   {:label "Cambridge Community Safety" :value "cambridge_communitysafety"}
   {:label "Cambridge Taking Part" :value "cambridge_takingpart"}

   ;; WARM
   {:label "Cambridge Resilience (WARM)" :value "cambridge_resilience"}
   {:label "Cambridge Wellbeing (WARM)" :value "cambridge_wellbeing"}

   ;; Crime
   {:label "Cambridge Anti-social Behaviour" :value "cambridge_crime_scrub_Anti-social_behaviour"}
   {:label "Cambridge Burglary" :value "cambridge_crime_scrub_Burglary"}
   {:label "Cambridge Criminal Damage and Arson" :value "cambridge_crime_scrub_Criminal_damage_and_arson"}
   {:label "Cambridge Drugs" :value "cambridge_crime_scrub_Drugs"}
   {:label "Cambridge Other Crime" :value "cambridge_crime_scrub_Other_crime"}
   {:label "Cambridge Other Theft" :value "cambridge_crime_scrub_Other_theft"}
   {:label "Cambridge Robbery" :value "cambridge_crime_scrub_Robbery"}
   {:label "Cambridge Shoplifting" :value "cambridge_crime_scrub_Shoplifting"}
   {:label "Cambridge Vehicle Crime" :value "cambridge_crime_scrub_Vehicle_crime"}
   {:label "Cambridge Total Crime" :value "cambridge_crime_scrub_Total_crime"}

   ;;
   ;; Hackney
   {:label "Hackney Broadband Speed" :value "hackney_broadband_speed"}
   {:label "Hackney Employment" :value "hackney_employment"}
   {:label "Hackney National Insurance Registrations" :value "hackney_nino"}

   ;; Accommodation
   {:label "Hackney Percent Flats (vs Houses)" :value "hackney_accommodation_type"}
   {:label "Hackney Percent Social Rented" :value "hackney_tenure"}
   {:label "Hackney Percent Over Occupied" :value "hackney_occupancy"}

   ;; Community
   {:label "Hackney Belonging" :value "hackney_belonging"}
   {:label "Hackney Community Cohesion" :value "hackney_communitycohesion"}
   {:label "Hackney Community Safety" :value "hackney_communitysafety"}
   {:label "Hackney Taking Part" :value "hackney_takingpart"}

   ;; WARM
   {:label "Hackney Resilience (WARM)" :value "hackney_resilience"}
   {:label "Hackney Wellbeing (WARM)" :value "hackney_wellbeing"}

   ;; Crime
   {:label "Hackney Anti-social Behaviour" :value "hackney_crime_scrub_Anti-social_behaviour"}
   {:label "Hackney Burglary" :value "hackney_crime_scrub_Burglary"}
   {:label "Hackney Criminal Damage and Arson" :value "hackney_crime_scrub_Criminal_damage_and_arson"}
   {:label "Hackney Drugs" :value "hackney_crime_scrub_Drugs"}
   {:label "Hackney Other Crime" :value "hackney_crime_scrub_Other_crime"}
   {:label "Hackney Other Theft" :value "hackney_crime_scrub_Other_theft"}
   {:label "Hackney Robbery" :value "hackney_crime_scrub_Robbery"}
   {:label "Hackney Shoplifting" :value "hackney_crime_scrub_Shoplifting"}
   {:label "Hackney Vehicle Crime" :value "hackney_crime_scrub_Vehicle_crime"}
   {:label "Hackney Total Crime" :value "hackney_crime_scrub_Total_crime"}])

;; Area

(defresource area-data [dir authorizer handlers]
  :authorized? dummy-authorizer ;;authorizer
  :available-media-types ["application/json"]
  :exists? (fn [{{{:keys [path]} :route-params} :request}]
             (when-let [res (io/file dir (str path ".js"))]
               {::resource res}))
  :handle-ok (fn [{{{:keys [path]} :route-params} :request res ::resource}]
               (when res
                 (-> (io/reader res)
                     (json/parse-stream keyword)
                     buckets))))

(defresource area-index [component authorizer]
  :authorized? dummy-authorizer
  :available-media-types ["application/edn"]
  :handle-ok (fn [context]
               (let [session (-> context :request cookies-request :cookies (get "session") :value)
                     session-details (session-id->session-details session component)
                     username (:username session-details)
                     user-area (-> component :protection-system :password-store :ref deref (get username) :area)]
                 (println "Getting Area Index")
                 (if username
                   (concat (public-area) user-area)
                   (public-area)))))

(defn make-area-api-handlers [dir component authorizer]
  (let [p (promise)]
    @(deliver p {:index (area-index component authorizer)
                 :data (area-data dir authorizer p)})))

(defn make-area-api-routes [handlers]
  ["" [["" (:index handlers)]
       [["/" :path] (:data handlers)]]])

(defn new-area-api-routes [dir context]
  (assert dir "No data dir")
  (assert (.exists (io/file dir)) (format "Directory doesn't exist: %s" dir))
  (component/using
            (new-bidi-routes
             (fn [component]
               (->> component
                    :protection-system
                    make-composite-authorizer
                    (make-area-api-handlers dir component)
                    make-area-api-routes))
              :context context)
             [:protection-system]))

;; Load/Save maps

(defresource maps-index [handlers database authorizer]
  :authorized? authorizer
  :available-media-types ["application/edn"]
  :handle-ok (fn [{{username :username} :auth-request}]
               (assert username)
               (for [map (index database username)]
                 (assoc (get-map database username map) :map map))))

(defresource maps-item [handlers database authorizer]
  :authorized? authorizer
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

(defn new-maps-api-routes [context]
  (component/using
   (new-bidi-routes
    (fn [component]
      (->> component
           :protection-system
           make-composite-authorizer
           (make-maps-api-handlers (:database component))
           make-maps-api-routes))
    :context context)
   [:protection-system :database]))

