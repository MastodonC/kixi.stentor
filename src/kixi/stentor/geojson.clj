(ns kixi.stentor.geojson
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [cheshire.core :as json]))

(defn ->location-code-key [location-cd]
  (cond (= location-cd "OA")   :OA11CD
        (= location-cd "LSOA") :LSOA11CD
        (= location-cd "MSOA") :MSOA11CD))

(def geo-json-files
  {:OA11CD "/home/bld/wip/stentor/data/geojson/output-area-generalised.geojson"
   :LSOA11CD "/home/bld/wip/stentor/data/geojson/lower-super-output-area-generalised.geojson"
   :MSOA11CD "/home/bld/wip/stentor/data/geojson/medium-super-output-area.geojson"})

;; This will eventually hit Cassandra or similar
(defn get-features [location-code-key feature-codes]
  (println (format "Looking for %s keys in %s" (count feature-codes) (location-code-key geo-json-files)))
  (let [geojson (with-open [rdr (io/reader (location-code-key geo-json-files))]
                  (json/parse-stream rdr keyword))]
    (filter #(feature-codes (get-in % [:properties location-code-key])) (:features geojson))))

;; TODO: make a multimethod that handles our different file
;; formats. Start with the simple one.
(defn csv->lookup [data]
  (reduce #(assoc %1 (first %2) %2) {} data))

;; NOTE I need to pass up the header vector to keep the order when I graph
(defn csv->geojson
  "Pass in a csv seq and a geojson map and get back an enriched and
  filtered geojson back."
  [csv-file-name out-name]
  (println (format "IN: %s OUT: %s" csv-file-name out-name))
  (let [data              (csv/read-csv (slurp csv-file-name))
        header            (first data)
        location-code-key (->location-code-key (first header))
        data-lookup       (csv->lookup (drop 1 data))
        features          (get-features location-code-key (set (keys data-lookup)))
        enriched-features (map #(update-in
                                 % [:properties]
                                 assoc :v
                                 (Double/parseDouble (second (get data-lookup (get-in % [:properties location-code-key])))))
                               features)]
    (with-open [out (io/writer out-name)]
      (json/generate-stream
       {:type "FeatureCollection"
        :features enriched-features
        :header header}
       out))))

(comment

  (let [data-dir ;;"/home/bld/data/data_stentor/public/choropleth/"
        "/home/bld/wip/stentor/tmp/public/choropleth/"]
    (doseq [file (for [city      ["sutton"
                                  ;; "camden"
                                  ;; "southwark"
                                  ;; "stoke" "staffs" "worcs"
                                  ]
                       file-type ["_accommodation_type"
                                  "_belonging"
                                  "_communitycohesion"
                                  "_communitysafety"
                                  "_crime_scrub_Anti-social_behaviour"
                                  "_crime_scrub_Burglary"
                                  "_crime_scrub_Criminal_damage_and_arson"
                                  "_crime_scrub_Drugs"
                                  "_crime_scrub_Other_crime"
                                  "_crime_scrub_Other_theft"
                                  "_crime_scrub_Robbery"
                                  "_crime_scrub_Shoplifting"
                                  "_crime_scrub_Total_crime"
                                  "_crime_scrub_Vehicle_crime"
                                  "_employment"
                                  "_nino"
                                  "_occupancy"
                                  "_resilience"
                                  "_takingpart"
                                  "_tenure"]]
                   (str city file-type))]
      (kixi.stentor.geojson/csv->geojson (str data-dir file ".csv")
                                         (str data-dir file ".js"))))
  )


(defn poi-csv->geojson [csv-file-name out-name]
  (let [data     (csv/read-csv (slurp csv-file-name))
        features (mapv (fn [[lat-lng v & rest]]
                         (let [[lat lng] (clojure.string/split lat-lng #"\|")]
                           {:type "Feature"
                            :geometry {:type "Point" :coordinates [(Double/parseDouble lng) (Double/parseDouble lat)]}
                            :properties {:v (Double/parseDouble v)}}))
                       (drop 1 data))]
    (with-open [out (io/writer out-name)]
      (println (format "IN: %s OUT: %s" csv-file-name out-name))
      (json/generate-stream 
       {:type "FeatureCollection"
        :features features}
       out))))

(comment

  ;; public choropleth
  (let [data-dir "/home/bld/data/data_stentor/public/choropleth/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/csv->geojson (str data-dir f ".csv")
                                           (str data-dir f ".js")))
     ["bexley_accommodation_type"
      "bexley_belonging"
      "bexley_broadband_speed"
      "bexley_communitycohesion"
      "bexley_communitysafety"
      ;; "bexley_crime"
      "bexley_crime_scrub_Anti-social_behaviour"
      "bexley_crime_scrub_Burglary"
      "bexley_crime_scrub_Criminal_damage_and_arson"
      ;; "bexley_crime_scrub"
      "bexley_crime_scrub_Drugs"
      "bexley_crime_scrub_Other_crime"
      "bexley_crime_scrub_Other_theft"
      "bexley_crime_scrub_Robbery"
      "bexley_crime_scrub_Shoplifting"
      "bexley_crime_scrub_Total_crime"
      "bexley_crime_scrub_Vehicle_crime"
      "bexley_employment"
      "bexley_nino"
      "bexley_occupancy"
      "bexley_resilience"
      "bexley_takingpart"
      "bexley_tenure"
      "cambridge_accommodation_type"
      "cambridge_belonging"
      "cambridge_broadband_speed"
      "cambridge_communitycohesion"
      "cambridge_communitysafety"
      ;; "cambridge_crime"
      "cambridge_crime_scrub_Anti-social_behaviour"
      "cambridge_crime_scrub_Burglary"
      "cambridge_crime_scrub_Criminal_damage_and_arson"
      ;;"cambridge_crime_scrub"
      "cambridge_crime_scrub_Drugs"
      "cambridge_crime_scrub_Other_crime"
      "cambridge_crime_scrub_Other_theft"
      "cambridge_crime_scrub_Robbery"
      "cambridge_crime_scrub_Shoplifting"
      "cambridge_crime_scrub_Total_crime"
      "cambridge_crime_scrub_Vehicle_crime"
      "cambridge_demographicslibraryusers"
      "cambridge_demographicslibraryvisits"
      "cambridge_employment"
      "cambridge_nino"
      "cambridge_occupancy"
      "cambridge_resilience"
      "cambridge_takingpart"
      "cambridge_tenure"
      "hackney_accommodation_type"
      "hackney_belonging"
      "hackney_broadband_speed"
      "hackney_communitycohesion"
      "hackney_communitysafety"
      ;; "hackney_crime"
      "hackney_crime_scrub_Anti-social_behaviour"
      "hackney_crime_scrub_Burglary"
      "hackney_crime_scrub_Criminal_damage_and_arson"
      ;; "hackney_crime_scrub"
      "hackney_crime_scrub_Drugs"
      "hackney_crime_scrub_Other_crime"
      "hackney_crime_scrub_Other_theft"
      "hackney_crime_scrub_Robbery"
      "hackney_crime_scrub_Shoplifting"
      "hackney_crime_scrub_Total_crime"
      "hackney_crime_scrub_Vehicle_crime"
      "hackney_employment"
      "hackney_nino"
      "hackney_occupancy"
      "hackney_resilience"
      "hackney_takingpart"
      "hackney_tenure"
      ;; "national_accommodationtype"
      ;; "national_broadbandspeed"
      ;; "national_crime"
      ;; "national_crime_scrub_Anti-social_behaviour"
      ;; "national_crime_scrub_Burglary"
      ;; "national_crime_scrub_Criminal_damage_and_arson"
      ;; "national_crime_scrub"
      ;; "national_crime_scrub_Drugs"
      ;; "national_crime_scrub_Other_crime"
      ;; "national_crime_scrub_Other_theft"
      ;; "national_crime_scrub_Robbery"
      ;; "national_crime_scrub_Shoplifting"
      ;; "national_crime_scrub_Total_crime"
      ;; "national_crime_scrub_Vehicle_crime"
      ;; "national_employment"
      ;; "national_nino"
      ;; "national_occupancy"
      ;; "national_tenure"
      ]))

  (let [data-dir "/home/bld/data/data_stentor/public/choropleth/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/csv->geojson (str data-dir f ".csv")
                                           (str data-dir f ".js")))
     ["bexley_wellbeing"
      "cambridge_wellbeing"
      "hackney_wellbeing"]))

  ;; private choropleth
  (let [data-dir "/home/bld/data/data_stentor/private/choropleth/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/csv->geojson (str data-dir f ".csv")
                                           (str data-dir f ".js")))
     ["bexley_adultcare"
      "bexley_oxleasclients"
      "bexley_recycledwastenonpaper"
      "bexley_recycledwastepaper"
      "bexley_residualwaste"
      "cambridge_futurebroadband"]))
  
  ;; public poi
  (let [data-dir "/home/bld/data/data_stentor/public/poi/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/poi-csv->geojson (str data-dir f ".csv")
                                               (str data-dir f ".js")))
     []))
  
  ;; private poi
  (let [data-dir "/home/bld/data/data_stentor/private/poi/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/poi-csv->geojson (str data-dir f ".csv")
                                               (str data-dir f ".js")))
     ["cambridge_librarycomputeruselastyear"
      "hackney_complaintslocations"
      "hackney_overcrowding"
      "hackney_rentarrears"
      "hackney_tenure"
      "hackney_underoccupancy"]))

  (let [data-dir "/home/bld/data/data_stentor/private/poi/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/poi-csv->geojson (str data-dir f ".csv")
                                               (str data-dir f ".js")))
     ["cambridge_digitallyexcluded"]))

  (let [data-dir "/home/bld/data/data_stentor/public/poi/"]
    (map
     #(let [f %]
        (kixi.stentor.geojson/poi-csv->geojson (str data-dir f ".csv")
                                               (str data-dir f ".js")))
     ["schools_hackney"]))
  
  )

