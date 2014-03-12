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
   [ankha.core :as ankha]))

(enable-console-print!)

(def app-model
  (atom
   {:poi-layer nil
    :poi-selector [{:label "Bydureon" :value "bydureon"}
                   {:label "Exenatide" :value "exenatide"}
                   {:label "Liraglutide" :value "liraglutide"}
                   {:label "Prucalopride" :value "prucalopride"}
                   {:label "Rivaroxaban 15mg" :value "rivaroxaban-15"}
                   {:label "Rivaroxaban 20mg" :value "rivaroxaban-20"}]}))

(def tile-url "http://{s}.tile.cloudmade.com/84b48bab1db44fb0a70c83bfc087b616/997/256/{z}/{x}/{y}.png")

(defn create-map
  [id]
  (let [m (-> js/L
              (.map id)
              (.setView (array 53.0 -1.5) 6))
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
  (println "compacted postcode:" (string/replace postcode #"\w+" ""))
  (str "http://data.ordnancesurvey.co.uk/doc/postcodeunit/" (string/replace postcode #"\w+" "") ".json"))

(defn to-postcode-url> [ch]
  (map> to-postcode-url ch))



(defn points-of-interest [data owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (html
       [:section
         [:h2 "Points of Interest"]
         [:select
          {:onChange
           (fn [e]
             (let [value (.-value (.-target e))]
               ;; TODO don't use json GETs!! see
               ;; https://github.com/yogthos/cljs-ajax using
               ;; ajax-request instead of GET because Julian says
               ;; to due to a bug, see discussion here:
               ;; https://github.com/yogthos/cljs-ajax/issues/38
               (if (= value "None")
                 (om/update! data :poi-layer-to-remove (:poi-layer @data))
                 (GET (str "/data/geojson/" value)
                     {:handler (fn [body]
                                 (let [json (clj->js body)
                                       layer (-> js/L (.geoJson json))]
                                   (om/update! data :poi-layer-to-remove (:poi-layer @data))
                                   (om/update! data :poi-layer-to-add layer)))
                      :response-format :json}))))}
          [:option "None"]
          (for [{:keys [label value]} (:poi-selector data)]
            [:option {:value value} label])]]))))

(defn postcode-selector [data owner]
  (reify
    om/IInitState
    (init-state [this]
      {:in (chan (sliding-buffer 1))
       :out (chan (sliding-buffer 1))})

    om/IWillMount
    (will-mount [this] nil)

    om/IRenderState
    (render-state [this state]
      (html
       [:section
        [:h2 "Zoom to postcode"]
        [:input {:type "text"}]
        [:button {:onClick (fn [_] (println "postcode clicked"))} "Zoom"]]
       ))
    )
  )

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
        (om/build postcode-selector app-state)
        (om/build map-saver app-state)]))))

;; http://data.ordnancesurvey.co.uk/doc/postcodeunit/HA99HD.json

(defn map-component
  "put the leaflet map as state in the om component"
  [{:keys [selection] :as app-state} owner]
  (reify

    om/IRender
    (render [this]
      (html [:div#map]))

    om/IDidMount
    (did-mount [this]
      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (create-map node)]
        (om/set-state! owner :map map)))

    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (om/get-state owner :map)
            ]
        (when-let [layer (:poi-layer-to-remove app-state)]
          (.removeLayer leaflet-map layer)
          (om/update! app-state :poi-layer-to-remove nil)
          (om/update! app-state :poi-layer nil))

        (when-let [layer (:poi-layer-to-add app-state)]
          (.addLayer leaflet-map layer)
          (om/update! app-state :poi-layer-to-add nil)
          (om/update! app-state :poi-layer layer))))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})
(om/root panel-component app-model {:target (. js/document (getElementById "panel"))})

(om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})
