(ns kixi.stentor.main
  (:require
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [html]]))

(enable-console-print!)

(println "Hello Bruce!")

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

(defn map-component
  "put the leaflet map as state in the om component"
  [{:keys [selection] :as app-state} owner]
  (println "owner: " owner)
  (println "selection: " selection)
  (reify
    om/IRender
    (render [this]
      (html [:div#map {:ref "map"}]))

    om/IDidMount
    (did-mount [this]
      (let [node                          (om/get-node owner)
            {:keys [leaflet-map] :as map} (create-map node)]

        (om/set-state! owner :map map)))))

(om/root map-component {} {:target (. js/document (getElementById "mappy"))})
(println "map should be there")

(defn widget [data]
  (om/component
   (html [:div "Hello world!"
          [:ul (for [n (range 1 10)]
                 [:li n])]
          (html/submit-button "React!")])))

;;(om/root widget {} {:target (. js/document (getElementById "app"))})
