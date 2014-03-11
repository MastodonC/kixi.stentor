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

(ns kixi.stentor.api
  (:require
   [modular.bidi :refer (new-bidi-routes)]
   [liberator.core :refer (defresource)]))

(defresource index [handlers]
  :available-media-types ["text/html"]
  :handle-ok "OK, I'm an API, how are you?")

(defn make-api-handlers []
  (let [p (promise)]
    @(deliver p
              {:index (index p)})))

(defn make-api-routes [handlers]
  ["/"
   [["index" (:index handlers)]]])

(defn new-api-routes []
  (-> (make-api-handlers)
      make-api-routes
      (new-bidi-routes "/data")))
