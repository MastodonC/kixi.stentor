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
    :poi-selector [{:label "Hackney Schools Population (Dummy Data)" :value "schools_hackney"}
                   ;; {:label "Cambridge Library Computer Use Last Year" :value "cambridge_librarycomputeruselastyear"}
                   ;; {:label "Cambridge Digitally Excluded" :value "cambridge_digitallyexcluded"}
                   ]
    ;; [ ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;;  ;; Hackney
    ;;  {:label "Hackney Real Time Complaints" :value "hackney_complaints_locations_anon"}
    ;;  {:label "Hackney Percentage Houses Overcrowded" :value "hackney_overcrowding_anon"}
    ;;  {:label "Hackney Percentage Houses in Rent Arrears" :value "hackney_rent_arrears_anon"}
    ;;  {:label "Hackney MePercent Social Rented in Years" :value "hackney_tenure_anon"}
    ;;  {:label "Hackney Percentage Houses Underoccupied" :value "hackney_underoccupancy_anon"}
    ;;  {:label "Hackney School Pupil Numbers" :value "hackney_schools_hackney"}

    ;;  ;;
    ;;  ;; Cambs
    ;;  {:label "Cambridgeshire Library Computer Use Last Year" :value "cambs_library_computeruse_lastyear"}]

    :area-layer-value nil
    :area-layer nil
    :area-selector [;;
                    ;; Bexley
                    {:label "Bexley Percent Unemployed" :value "bexley_employment"}
                    {:label "Bexley National Insurance Registrations" :value "bexley_nino"}
                    {:label "Bexley Broadband Speed" :value "bexley_broadband_speed"}

                    ;; Waste
                    ;; {:label "Bexley Recycled Waste Non-paper" :value "bexley_recycledwastenonpaper"}
                    ;; {:label "Bexley Recycled Waste Paper" :value "bexley_recycledwastepaper"}
                    ;; {:label "Bexley Residual Waste" :value "bexley_residualwaste"}

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

                    ;;
                    ;; Cambridge
                    {:label "Cambridge Broadband Speed" :value "cambridge_broadband_speed"}
                    ;; {:label "Cambridge Future Broadband" :value "cambridge_futurebroadband"}

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
                    {:label "Hackney Total Crime" :value "hackney_crime_scrub_Total_crime"}
                    ]

    :maps []

    :leaflet-map nil                    ; authoritative
    :map {:lat 51.5478 :lng -0.0547}    ; not authoritative

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

;; When we get a 401 go to the login screen
(defn authz-error-handler [response]
  (println "Authorization failure. Redirecting to login.")
  (println "Error: " response)
  (.. js/window -location (replace "login")))

;;Ajax Error Handler
(defn error-handler [{:keys [status status-text] :as response}]
  (if (= 401 status)
    (authz-error-handler response)
    (println (str "Error: " status " " status-text))))

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
                                              (color/brewer :Blues 7 (.. feature -properties -bucket))
                                              "weight" 1
                                              "opacity" 0.8
                                              "color" "#08306b"
                                              "fillOpacity" 0.6))

                                           "onEachFeature"
                                           (fn [feature layer]
                                             (.on layer #js {:click (fn [e] (println "Event: " e))}))

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
         :error-handler #(error-handler %)
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
                                           #js {:style
                                                (fn [feature]
                                                  #js {:fillColor
                                                       (color/brewer :YlGn 7 (.. feature -properties -bucket))
                                                       :weight 1
                                                       :color "#eee"
                                                       :fillOpacity 0.8}
                                                  )

                                                :onEachFeature
                                                (fn [feature layer]
                                                  (.on layer
                                                       #js {:click
                                                            (fn [e]
                                                              (let [props (.. e -target -feature -properties)]
                                                                (om/update! data :area-feature-data props)))}))}))]
                       (om/update! data :area-layer-value value)
                       (om/update! data :area-layer-to-remove (:area-layer @data))
                       (om/update! data :area-layer-to-add layer)))
          :error-handler #(error-handler %)
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

(defn area-info-component [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (let [props (js->clj (:area-feature-data data) :keywordize-keys true)]
        (html
         [:section
          [:h2 "Info"]
          ;; [:p "Descriptive text about " (:label (:area-selector data))]
          [:p [:strong "Area: "]
           (get props :LSOA11CD (get props :OA11CD (get props :MSOA11CD "None Selected")))
           " "
           (when-let [area-name (get props :LSOA11NM (get props :MSOA11NM))]
             area-name)]
          [:p [:strong "Value: "] (get props :v "None Selected")]])))))

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
       :handler #(om/update! data :maps %)
       :error-handler #(println "Maps not available unless logged in. " %)}))

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
       :handler (fn [_] (get-maps data))
       :error-handler #(error-handler %)}))

(defn map-saver-component [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Save current map"]
        [:input {:id "maplabel-input" :type "text"
                 :onChange (fn [e] (om/set-state! owner :mapname (.-value (.-target e))))
                 :onKeyPress (fn [e] (when (= (.-keyCode e) 13) (save-map data owner)))
                 :placeholder "Save as..."}]
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
        [:h1 "Stentor"]
        (om/build points-of-interest-component app-state)
        (om/build area-component app-state)
        (om/build area-info-component app-state)
        (om/build postcode-selector-component app-state)
        ;;(om/build map-saver-component app-state)
        ;;(om/build map-loader-component app-state)
        ]))))

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

          ;; FIXME: Is this the right place to do this?
          ;; blat the area-feature-data when the layer changes
          (om/update! app-state :area-feature-data nil)

          (om/update! app-state :area-layer-to-remove nil)
          (om/update! app-state :area-layer nil))

        (when-let [layer (:poi-layer-to-add app-state)]
          (.addLayer leaflet-map layer)
          (om/update! app-state :poi-layer-to-add nil)
          (om/update! app-state :poi-layer layer))

        (when-let [layer (:area-layer-to-add app-state)]
          (.addLayer leaflet-map layer)
          ;; snap to the layer bounds when layer is not in viewport
          (when-not (.intersects (.getBounds leaflet-map) (.getBounds layer))
            (.fitBounds leaflet-map (.getBounds layer)))
          (om/update! app-state :area-layer-to-add nil)
          (om/update! app-state :area-layer layer))

        (when-let [layer (:poi-layer app-state)]
          (when (.-_map layer)
              (.bringToFront layer)))))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})
(om/root panel-component app-model {:target (. js/document (getElementById "panel"))})

;; (om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})
