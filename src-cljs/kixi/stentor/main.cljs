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
   [ajax.core :refer (GET PUT ajax-request)]
   [ankha.core :as ankha]
   [kixi.stentor.colorbrewer :as color]
   [goog.events :as events]))

(enable-console-print!)

(def app-model
  (atom
   {:poi-layer-value nil
    :poi-layer nil
    :poi-selector [{:label "Real Time Complaints" :value "complaints_locations_anon"}
                   {:label "Percentage Houses Overcrowded" :value "overcrowding_anon"}
                   {:label "Percentage Houses in Rent Arrears" :value "rent_arrears_anon"}
                   {:label "Median Tenure in Years" :value "tenure_anon"}
                   {:label "Percentage Houses Underoccupied" :value "underoccupancy_anon"}
                   {:label "School Pupil Numbers" :value "schools_hackney"}]

    :area-layer-value nil
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
    :maps []

    :leaflet-map nil ; authoritative
    :map {:lat 51.5478 :lng -0.0547} ; not authoritative
}))

(def tile-url "http://{s}.tile.cloudmade.com/84b48bab1db44fb0a70c83bfc087b616/997/256/{z}/{x}/{y}.png")

(defn create-map
  [cursor id]
  (let [m (-> js/L
              (.map id)
              (.setView (array (:lat cursor) (:lng cursor)) 13))
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
  (color/brewer scheme steps idx))

(defn update-poi [data value]
  (if-not value
    (do
      (om/update! data :poi-layer-value nil)
      (om/update! data :poi-layer-to-remove (:poi-layer @data)))
    ;; TODO don't use json GETs!! see
    ;; https://github.com/yogthos/cljs-ajax using
    ;; ajax-request instead of GET because Julian says
    ;; to due to a bug, see discussion here:
    ;; https://github.com/yogthos/cljs-ajax/issues/38
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
                                              "fillOpacity" 0.6))

                                           "pointToLayer"
                                           (fn [feature latlng]
                                             (-> js/L
                                                 (.circleMarker
                                                  latlng
                                                  (js-obj
                                                   "radius" 8)))))))]
                      (om/update! data :poi-layer-value value)
                      (om/update! data :poi-layer-to-remove (:poi-layer @data))
                      (om/update! data :poi-layer-to-add layer)))
         :response-format :json})))

(defn update-area [data value]
  (if-not value
    (do
      (om/update! data :area-layer-value nil)
      (om/update! data :area-layer-to-remove (:area-layer @data)))
    ;; TODO don't use json GETs!! see
    ;; https://github.com/yogthos/cljs-ajax using
    ;; ajax-request instead of GET because Julian says
    ;; to due to a bug, see discussion here:
    ;; https://github.com/yogthos/cljs-ajax/issues/38
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
                      (om/update! data :area-layer-value value)
                      (om/update! data :area-layer-to-remove (:area-layer @data))
                      (om/update! data :area-layer-to-add layer)))
         :response-format :json})))

(defn points-of-interest-component [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Points of interest"]
        [:select {:onChange (fn [e] (let [val (let [v (.-value (.-target e))] (if (= v "None") nil v))]
                                      (update-poi data val)))
                  :value (:poi-layer-value data)}
         [:option "None"]
         (for [{:keys [label value]} (:poi-selector data)]
           [:option {:value value} label])]]))))

(defn area-component [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Areas"]
        [:select {:onChange (fn [e] (let [val (let [v (.-value (.-target e))] (if (= v "None") nil v))]
                                      (update-area data val)))
                  :value (:area-layer-value data)}
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
                          lng (get-in body [(str "http://data.ordnancesurvey.co.uk/id/postcodeunit/" postcode)
                                            "http://www.w3.org/2003/01/geo/wgs84_pos#long"
                                            0 "value"])]
                      (when-let [map (:leaflet-map @data)]
                        (.panTo map (clj->js {:lat (js/parseFloat lat)
                                              :lng (js/parseFloat lng)})))))

         :response-format :json})))

(defn postcode-selector-component [data owner]
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
         {:onClick (fn [_]
                     (pan-to-postcode data owner))}
         "Go"]]))))

(defn get-maps [data]
  (GET "/maps"
      {:response-format :edn
       :handler #(om/update! data :maps %)}))

(defn save-map [data owner]
  (PUT (str "/maps/" (.toLowerCase (string/replace (om/get-state owner :mapname) #"[\s]+" "")))
      { ;; Even though there is no response body, we need to set the
       ;; response-format to raw otherwise the handler doesn't get
       ;; called.
       :response-format :raw
       :params (if-let [lmap (:leaflet-map @data)]
                 (let [center (.getCenter lmap)
                       zoom (.getZoom lmap)]
                   {:latlng [(.-lat center) (.-lng center)]
                    :zoom zoom
                    :poi (:poi-layer-value @data)
                    :area (:area-layer-value @data)})
                 ;; A version that can run without a map component present
                 {:latlng [50 0]
                  :zoom 10
                  :poi (:poi-layer-value @data)
                  :area (:area-layer-value @data)})
       :format :edn
       :handler (fn [_] (get-maps data))}))

(defn map-saver-component [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Save current map"]
        [:label {:for "maplabel-input"} "Save As"]
        [:input {:id "maplabel-input" :type "text"
                 :onChange (fn [e] (om/set-state! owner :mapname (.-value (.-target e))))
                 :onKeyPress (fn [e] (when (= (.-keyCode e) 13) (save-map data owner)))}]
        [:button {:onClick (fn [_] (save-map data owner))} "Save"]]
       ))))

(defn load-map [data m]
  (when-let [map (:leaflet-map @data)]
    (.panTo map (clj->js (zipmap [:lat :lng] (:latlng m))))
    (.setZoom map (:zoom m)))
  (let [val (:poi m)]
    (om/update! data :poi-layer-value val)
    (update-poi data val))
  (let [val (:area m)]
    (om/update! data :area-layer-value val)
    (update-area data val)))

(defn map-loader-component [data owner]
  (reify
    om/IDidMount
    (did-mount [this] (get-maps data))
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Load maps"]
        (for [m (sort-by :map (:maps data))]
          [:p [:a {:href "#"
                   :onClick (fn [e]
                              (load-map data m)
                              (.preventDefault e))}
               (:map m)]])]))))

(defn panel-component
  [app-state owner]
  (reify

    om/IRender
    (render [this]
      (html
       [:div
        (om/build points-of-interest-component app-state)
        (om/build area-component app-state)
        (om/build postcode-selector-component app-state)
        (om/build map-saver-component app-state)
        (om/build map-loader-component app-state)]))))

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
            {:keys [leaflet-map] :as map} (create-map (:map app-state) node)
            loc {:lng (get-in app-state [:map :lng])
                 :lat (get-in app-state [:map :lat])}]

        ;;(.on leaflet-map "click" (fn [ev] (.dir js/console ev)))
        (.on leaflet-map "moveend" (fn [ev]
                                     (let [center (.getCenter leaflet-map)]
                                       (.dir js/console center)
                                       (om/update! app-state [:map :lng] (.-lng center))
                                       (om/update! app-state [:map :lat] (.-lat center)))
                                     ))

        (.panTo leaflet-map (clj->js loc))

        (om/set-state! owner :map map)

        (om/update! app-state :leaflet-map leaflet-map)
        ))

    om/IDidUpdate
    (did-update [this prev-props prev-state]

      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (om/get-state owner :map)]

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
          (om/update! app-state :area-layer layer))

        (when-let [layer (:poi-layer app-state)]
          (.bringToFront layer))))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})
(om/root panel-component app-model {:target (. js/document (getElementById "panel"))})

;; (om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})
