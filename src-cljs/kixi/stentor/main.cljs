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
  (:require
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [html]]
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

(defn poi-selector-component
  [app-state owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div#poi
        [:h2 "Points of Interest"]
        [:form
         [:select
          {:onChange
           (fn [e]
             (let [value (.-value (.-target e))]
               ;; TODO don't use json GETs!! see
               ;; https://github.com/yogthos/cljs-ajax using
               ;; ajax-request instead of GET because Julian says
               ;; to due to a bug, see discussion here:
               ;; https://github.com/yogthos/cljs-ajax/issues/38
               (GET (str "/data/geojson/" value)
                             {:handler (fn [json]
                                         (let [data (clj->js json)
                                               layer (-> js/L (.geoJson data))]
                                           (om/update! app-state :poi-layer-to-remove (:poi-layer @app-state))
                                           (om/update! app-state :poi-layer-to-add layer)))
                              :response-format :json})))}
          [:option "None"]
          (for [{:keys [label value]} (:poi-selector app-state)]
            [:option {:value value} label])]]]))))

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
          (om/update! app-state :poi-layer layer))

        ))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})
(om/root poi-selector-component app-model {:target (. js/document (getElementById "poi"))})

(om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})
