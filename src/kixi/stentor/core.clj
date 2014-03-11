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

(ns kixi.stentor.core
  (:require
   [modular.http-kit :refer (new-webserver)]
   [modular.bidi :refer (new-bidi-routes new-bidi-ring-handler-provider)]
   [bidi.bidi :as bidi]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]))

(defprotocol Menuitem
  (attributes [_]))

(defrecord Database []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn new-database []
  (new Database))

(defrecord Menu []
  component/Lifecycle
  (start [this]
    (update-in this [:menuitems]
               (partial map attributes)))
  (stop [this]
    this))

(defn new-menu []
  (new Menu))

(defrecord AboutMenuitem [label]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)
  Menuitem
  (attributes [this]
    {:label (str label "...")}))

(defn index [handlers-p]
  (fn [req]
    {:status 200 :body (slurp (io/resource "index.html"))}))

(defn make-handlers []
  (let [p (promise)]
    @(deliver p
              {:index (index p)})))

(defn make-routes [handlers]
  ["/"
   [["" (:index handlers)]
    ["" (bidi/->ResourcesMaybe {})]
    ]])

(defn new-main-routes []
  (new-bidi-routes (make-routes (make-handlers)) ""))

(defn make-data-handlers []
  (let [p (promise)]
    @(deliver p
              {:index (index p)})))

(defn make-data-routes [handlers]
  ["/"
   [["" (:index handlers)]
    ;;["" (bidi/->ResourcesMaybe {})]
    ]])

(defn new-data-routes []
  (new-bidi-routes (make-data-routes (make-data-handlers)) "/data"))
