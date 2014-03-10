(ns kixi.stentor.main
  (:require
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [html]]
   [ajax.core :refer (GET POST)]
   [ankha.core :as ankha]))

(enable-console-print!)

(def app-model
  (atom
   {:layers []}))

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
  (reify

    om/IWillMount
    (will-mount [this]
      (println "will mount")
      ;; TODO don't use json GETs!! see https://github.com/yogthos/cljs-ajax
      (GET "/geojson.js"
          {:handler (fn [e]
                      (println "here")
                      (let [data (clj->js e)]
                        (println "type" (type data))
                        (.dir js/console data)
                        (let [layer (-> js/L (.geoJson data))]
                          (.log js/console "layer is" layer)
                          (om/transact! app-state [:layers] #(conj % layer))
                          )))
           :response-format :json}))

    om/IRender
    (render [this]
      (println "Rendering!")
      (html [:div#map {:ref "map"}]))

    om/IDidMount
    (did-mount [this]
      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (create-map node)]
        (om/set-state! owner :map map)))

    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (let [node (om/get-node owner)
            {:keys [leaflet-map] :as map} (om/get-state owner :map)
            layers (:layers app-state)]
        ;; remove all the layers
        (doseq [layer layers]
          (.addLayer leaflet-map layer))))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})

(defn widget [data]
  (om/component
   (html [:div "Hello world!"
          [:ul (for [n (range 1 10)]
                 [:li n])]
          (html/submit-button "React!")])))

;;(om/root widget {} {:target (. js/document (getElementById "app"))})

(om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})
