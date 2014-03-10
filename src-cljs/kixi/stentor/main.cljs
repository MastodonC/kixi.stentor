(ns kixi.stentor.main
  (:require
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [html]]
   [ajax.core :refer (GET POST)]
   [ankha.core :as ankha]))

(enable-console-print!)

(def app-model
  (atom
   {:poi-layer nil
    :poi-selector [{:label "Layer 1" :value "A"}
                   {:label "Layer 2" :value "B"}
                   {:label "Layer 3" :value "C"}]}))

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
      (html [:div#poi
             [:h2 "Points of Interest"]
             [:form
              [:select
               {:onChange
                (fn [e]
                  (let [value (.-value (.-target e))]
                    (println "Getting URI" value)
                    (GET "/geojson.js"
                        {:handler (fn [json]
                                    (let [data (clj->js json)
                                          layer (-> js/L (.geoJson data))]
                                      (om/update! app-state :layer-to-remove (:poi-layer @app-state))
                                      (when (= "A" value)
                                        (om/update! app-state :layer-to-add layer))))
                         :response-format :json}))
                  )}
               [:option "None"]
               (for [{:keys [label value]} (:poi-selector app-state)]
                 [:option {:value value} label]
                 )
               ]]]))))

(defn map-component
  "put the leaflet map as state in the om component"
  [{:keys [selection] :as app-state} owner]
  (reify

    om/IWillMount
    (will-mount [this]
      (println "will mount")
      ;; TODO don't use json GETs!! see https://github.com/yogthos/cljs-ajax
      )

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
        (when-let [layer (:layer-to-remove app-state)]
          (.removeLayer leaflet-map layer)
          (om/update! app-state :layer-to-remove nil)
          (om/update! app-state :poi-layer nil))

        (when-let [layer (:layer-to-add app-state)]
          (.addLayer leaflet-map layer)
          (om/update! app-state :layer-to-add nil)
          (om/update! app-state :poi-layer layer))

        #_(println "poi value is " (:poi app-state))
        #_(when (= "A" (:poi app-state))
          (doseq [layer layers]
            (.addLayer leaflet-map layer)))))))

(om/root map-component app-model {:target (. js/document (getElementById "mappy"))})
(om/root poi-selector-component app-model {:target (. js/document (getElementById "poi"))})

(defn widget [data]
  (om/component
   (html [:div "Hello world!"
          [:ul (for [n (range 1 10)]
                 [:li n])]
          (html/submit-button "React!")])))

;;(om/root widget {} {:target (. js/document (getElementById "app"))})

(om/root ankha/inspector app-model {:target (.getElementById js/document "debug")})
