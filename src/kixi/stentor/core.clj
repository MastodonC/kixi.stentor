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
   [bidi.bidi :as bidi :refer (path-for ->Redirect ->ResourcesMaybe)]
   [clojure.java.io :as io]
   [hiccup.core :refer (html)]
   [ring.middleware.params :refer (wrap-params)]
   [ring.middleware.cookies :refer (wrap-cookies)]
   [cylon.core :refer (new-protected-bidi-routes)]
   [com.stuartsierra.component :as component]))

(defn- index [handlers]
  (fn [req]
    {:status 200 :body (slurp (io/resource "index.html"))}))

(defn make-main-handlers []
  (let [p (promise)]
    @(deliver p {:index (index p)})))

(defn make-main-routes [handlers]
  ["/"
   [["" (->Redirect 307 (:index handlers))]
    ["index.html" (:index handlers)]
    ["" (bidi/->ResourcesMaybe {})]]])

(defn new-main-routes [context]
  (new-protected-bidi-routes
   (make-main-routes (make-main-handlers))
   :context context))
