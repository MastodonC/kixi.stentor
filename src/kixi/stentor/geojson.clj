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
  {:OA11CD "/home/bld/data/geojson/output-area-generalised.geojson"
   :LSOA11CD "/home/bld/data/geojson/lower-super-output-area-generalised.geojson"})

;; This will eventually hit Cassandra or similar
(defn get-features [location-code-key feature-codes]
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

(defn poi-csv->geojson [csv-file-name out-name]
  (let [data     (csv/read-csv (slurp csv-file-name))
        features (mapv (fn [[lat-lng v & rest]]
                         (let [[lat lng] (clojure.string/split lat-lng #"\|")]
                           {:type "Feature"
                            :geometry {:type "Point" :coordinates [(Double/parseDouble lng) (Double/parseDouble lat)]}
                            :properties {:v (Double/parseDouble v)}}))
                       (drop 1 data))]
    (with-open [out (io/writer out-name)]
        (json/generate-stream 
         {:type "FeatureCollection"
          :features features}
         out))))
