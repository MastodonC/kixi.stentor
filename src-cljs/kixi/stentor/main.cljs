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

(ns kixi.stentor.main
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [clojure.string :as string]
   [sablono.core :as html :refer-macros [html]]
   [cljs.core.async :refer [<! >! chan put! sliding-buffer close! pipe map< filter< mult tap map>]]
   [ajax.core :refer (GET ajax-request)]
   [ankha.core :as ankha]
   [kixi.stentor.colorbrewer :as color]
   [goog.events :as events]))

(enable-console-print!)

(def app-model
  (atom
   {:poi-layer nil
    :poi-selector [{:label "Real Time Complaints" :value "complaints_locations_anon"}
                   {:label "Percentage Houses Overcrowded" :value "overcrowding_anon"}
                   {:label "Percentage Houses in Rent Arrears" :value "rent_arrears_anon"}
                   {:label "Median Tenure in Years" :value "tenure_anon"}
                   {:label "Percentage Houses Underoccupied" :value "underoccupancy_anon"}
                   {:label "School Pupil Numbers" :value "schools_hackney"}]
    :area-layer nil
    :area-selector [;; Acommodation
                    {:label "Percent Unemployed" :value "hackney-employment"}
                    {:label "Percent Flats (vs Houses)" :value "accommodationtype_oa_hackney"}
                    {:label "Percent Overoccupied" :value "occupancy_oa_hackney"}

                    ;; Schools
                    {:label "Percent Social Rented" :value "tenure_oa_hackney"}

                    ;; Warm
                    {:label "Belonging (WARM)" :value "belonging_oa_hackney"}
                    {:label "Community Cohesion (WARM)" :value "communitycohesion_lsoa_hackney"}
                    {:label "Community Safety (WARM)" :value "communitysafety_lsoa_hackney"}
                    {:label "Resilience (WARM)" :value "resilience_oa_hackney"}
                    {:label "Taking Part (WARM)" :value "takingpart_lsoa_hackney"}

                    ;; Crime
                    {:label "Total Crimes Last Year" :value "crime_summary_lsoa_hackney_narrow"}
                    {:label "Burglaries Last Year" :value "burglary_lsoa_hackney_narrow"}
                    {:label "Criminal Damage Incidents Last Year" :value "criminal_damage_lsoa_hackney_narrow"}
                    {:label "Drugs Crimes Last Year" :value "drugs_lsoa_hackney_narrow"}
                    {:label "Other Crimes Last Year" :value "other_crime_lsoa_hackney_narrow"}
                    {:label "Robberies Last Year" :value "robbery_lsoa_hackney_narrow"}
                    {:label "Theft & Handling Offences Last Year" :value "theft_and_handling_lsoa_hackney_narrow"}
                    {:label "Violence Against the Person Offences Last Year" :value "violence_lsoa_hackney_narrow"}
                    
                    ]
    :map {:lat 51.505 :lon -0.09}}))

(def tile-url "http://{s}.tile.cloudmade.com/84b48bab1db44fb0a70c83bfc087b616/997/256/{z}/{x}/{y}.png")

(defn create-map
  [cursor id]
  (let [m (-> js/L
              (.map id)
              (.setView (array (:lat cursor) (:lon cursor)) 13))
        tiles (-> js/L (.tileLayer
                        tile-url
                        {:maxZoom 16
                         :attribution "Map data &copy; 2011 OpenStreetMap contributors, Imagery &copy; 2012 CloudMade"}))]

    (.addTo tiles m)
    {:leaflet-map m}))

(defn update-when [x pred f & args]
  (if pred (apply f x args) x))

(defn ajax [{:keys [in out]} content-type]
  (go-loop []
    (when-let [url (<! in)]
      (GET url
           (-> {:handler #(put! out %)
                :headers {"Accept" content-type}
                :response-format :text}
               (update-when (= content-type "application/json") merge {:response-format :json :keywords? true})))
      (recur))))

(defn to-postcode-url [postcode]
  (str "http://data.ordnancesurvey.co.uk/doc/postcodeunit/" (string/replace postcode #"[\s]+" "") ".json"))

(defn to-postcode-url> [ch]
  (map> to-postcode-url ch))

(defn get-color [scheme steps idx]
  (let [color (color/brewer scheme steps idx)
        _     (println "scheme: " scheme " steps-key: " steps " idx-key: " idx " color: " color)]
    color))

(defn change-points [data]
  (fn [e]
    (let [value (.-value (.-target e))]
      ;; TODO don't use json GETs!! see
      ;; https://github.com/yogthos/cljs-ajax using
      ;; ajax-request instead of GET because Julian says
      ;; to due to a bug, see discussion here:
      ;; https://github.com/yogthos/cljs-ajax/issues/38
      (if (= value "None")
        (om/update! data :poi-layer-to-remove (:poi-layer @data))
        (GET (str  "/data/geojson-poi/" value)
            {:handler (fn [body]
                        (let [json (clj->js body)
                              layer (-> js/L (.geoJson
                                              json
                                              (js-obj
                                               "style"
                                               (fn [feature]
                                                 (js-obj
                                                  "fillColor"
                                                  (color/brewer :Blues 7 (aget feature "properties" "bucket"))
                                                  "weight" 1
                                                  "opacity" 0.8
                                                  "color" "#08306b"
                                                  "fillOpacity" 0.8))
                                               
                                               "pointToLayer"
                                               (fn [feature latlng]
                                                 (-> js/L
                                                     (.circleMarker
                                                      latlng
                                                      (js-obj
                                                       "radius" 8)))))))]
                          (om/update! data :poi-layer-to-remove (:poi-layer @data))
                          (om/update! data :poi-layer-to-add layer)))
             :response-format :json})))))

;; TODO - brutal copy-and-paste - better to duplicate than get the wrong abstraction
(defn change-area [data]
  (fn [e]
    (let [value (.-value (.-target e))]
      ;; TODO don't use json GETs!! see
      ;; https://github.com/yogthos/cljs-ajax using
      ;; ajax-request instead of GET because Julian says
      ;; to due to a bug, see discussion here:
      ;; https://github.com/yogthos/cljs-ajax/issues/38
      (if (= value "None")
        (om/update! data :area-layer-to-remove (:area-layer @data))
        (GET (str  "/data/geojson-area/" value)
            {:handler (fn [body]
                        (let [json (clj->js body)
                              layer (-> js/L (.geoJson
                                              json
                                              (js-obj "style"
                                                      (fn [feature]
                                                        (js-obj "fillColor"
                                                                (color/brewer :PuR 7 (aget feature "properties" "bucket"))
                                                                "weight" 1
                                                                "color" "#eee"
                                                                "fillOpacity" 0.8))
                                                      )))]
                          (om/update! data :area-layer-to-remove (:area-layer @data))
                          (om/update! data :area-layer-to-add layer)))
             :response-format :json})))))

(defn points-of-interest [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Points of interest"]
        [:select {:onChange (change-points data)}
         [:option "None"]
         (for [{:keys [label value]} (:poi-selector data)]
           [:option {:value value} label])]]))))

(defn area [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Areas"]
        [:select {:onChange (change-area data)}
         [:option "None"]
         (for [{:keys [label value]} (:area-selector data)]
           [:option {:value value} label])]]))))

(defn pan-to-postcode [data owner]
  (let [postcode (.toUpperCase (string/replace (om/get-state owner :postcode) #"[\s]+" ""))]
    (GET (str "http://data.ordnancesurvey.co.uk/doc/postcodeunit/" postcode ".json")
        {:handler (fn [body]
                    (let [lat (get-in body [(str "http://data.ordnancesurvey.co.uk/id/postcodeunit/" postcode)
                                            "http://www.w3.org/2003/01/geo/wgs84_pos#lat"
                                            0 "value"])
                          lon (get-in body [(str "http://data.ordnancesurvey.co.uk/id/postcodeunit/" postcode)
                                            "http://www.w3.org/2003/01/geo/wgs84_pos#long"
                                            0 "value"])]
                      (om/update! data [:map :lat] (js/parseFloat lat))
                      (om/update! data [:map :lon] (js/parseFloat lon))))

         :response-format :json})))

(defn postcode-selector [data owner]
  (reify
    om/IInitState
    (init-state [this]
      {:in (chan (sliding-buffer 1))
       :out (chan (sliding-buffer 1))
       :initialPostCode "E8 1LA"}) ;; Hackney Downs

    om/IWillMount
    (will-mount [this]
      (om/set-state! owner :postcode (om/get-state owner :initialPostCode)))

    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Zoom to postcode"]
        [:input {:type "text"
                 :defaultValue (:initialPostCode state)
                 :onChange (fn [e]
                             (om/set-state! owner :postcode (.-value (.-target e))))
                 :onKeyPress (fn [e] (when (= (.-keyCode e) 13)
                                       (pan-to-postcode data owner)))}]
        [:button
         {:onClick (fn [_] (pan-to-postcode data owner))}
         "Go"]]))))

(defn map-saver [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Save current map"]
        [:label {:for "maplabel-input"} "Save As"]
        [:input {:id "maplabel-input" :type "text"}]
        [:button {:onClick (fn [_] (println "TODO: save map!"))} "Save"]]
       ))))

(defn panel-component
  [app-state owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div
        (om/build points-of-interest app-state)
        (om/build area app-state)
        (om/build postcode-selector app-state)
        (om/build map-saver app-state)]))))

;; http://data.ordnancesurvey.co.uk/doc/postcodeunit/HA99HD.json
;;                     [51.505, -0.09]

(defn map-component
  "put the leaflet map as state in the om component"
  [app-state owner]
  (reify

    om/IRender
    (render [this]
      (html [:div#map]))

    om/IDidMount
    (did-mount [this]
      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (create-map (:map app-state) node)]
        (.on leaflet-map "click" (fn [ev] (.dir js/console ev)))

        (om/set-state! owner :map map)))

    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (om/get-state owner :map)
            loc {:lon (get-in app-state [:map :lon])
                 :lat (get-in app-state [:map :lat])}]
        (.panTo leaflet-map (clj->js loc))

        (when-let [layer (:poi-layer-to-remove app-state)]
          (.removeLayer leaflet-map layer)
          (om/update! app-state :poi-layer-to-remove nil)
          (om/update! app-state :poi-layer nil))

        (when-let [layer (:area-layer-to-remove app-state)]
          (.removeLayer leaflet-map layer)
          (om/update! app-state :area-layer-to-remove nil)
          (om/update! app-state :area-layer nil))

        (when-let [layer (:poi-layer-to-add app-state)]
          (.addLayer leaflet-map layer)
          (om/update! app-state :poi-layer-to-add nil)
          (om/update! app-state :poi-layer layer))

        (when-let [layer (:area-layer-to-add app-state)]
          (.addLayer leaflet-map layer)
          (om/update! app-state :area-layer-to-add nil)
          (om/update! app-state :area-layer layer))))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})
(om/root panel-component app-model {:target (. js/document (getElementById "panel"))})

;; (om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})

