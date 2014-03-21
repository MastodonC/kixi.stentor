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
   [bidi.bidi :as bidi :refer (path-for ->WrapMiddleware)]
   [clojure.java.io :as io]
   [hiccup.core :refer (html)]
   [ring.middleware.params :refer (wrap-params)]
   [ring.middleware.cookies :refer (wrap-cookies)]
   [modular.entrance :refer (protect
                             ->BidiFailedAuthorizationRedirect
                             authorized-user?
                             HttpRequestAuthorizer
                             UserPasswordAuthorizer
                             HttpSessionStore
                             new-session-based-request-authorizer
                             start-session! get-session)]
   [com.stuartsierra.component :as component]))

(defn- index [handlers-p]
  (fn [req]
    {:status 200 :body (slurp (io/resource "index.html"))}))

(defn- login-form [handlers]
  (fn [{{{requested-uri :value} "requested-uri"} :cookies
        routes :modular.bidi/routes}]
    {:status 200
     :body
     (html
      [:body
       [:h1 "Login"]
       [:form {:method "POST" :style "border: 1px dotted #555"
               :action (bidi/path-for routes (:login-handler @handlers))}
        (when requested-uri
          [:input {:type "hidden" :name :requested-uri :value requested-uri}])
        [:div
         [:label {:for "username"} "Username"]
         [:input {:id "username" :name "username" :type "input"}]]
        [:div
         [:label {:for "password"} "Password"]
         [:input {:id "password" :name "password" :type "password"}]]
        [:input {:type "submit" :value "Login"}]
        ]]
      )}))

(defn- login-handler [handlers {:keys [registry sessions]}]
  (assert (satisfies? UserPasswordAuthorizer registry))
  (assert (satisfies? HttpSessionStore sessions))
  (fn [{{username "username" password "password" requested-uri "requested-uri"} :form-params
        routes :modular.bidi/routes}]
    (if (and username
             (not-empty username)
             (authorized-user? registry (.trim username) password))
      {:status 302
       :headers {"Location" requested-uri}
       :cookies (start-session! sessions username)}

      ;; Return back to login form
      {:status 302
       :headers {"Location" (path-for routes (:login-form @handlers))}
       })))

(defn make-handlers [opts]
  (let [p (promise)]
    @(deliver p
              {:index (index p)
               :login-form (login-form p)
               :login-handler (login-handler p opts)})))

(defn make-routes [handlers {:keys [sessions]}]
  ["/"
   [["login" (->WrapMiddleware
              {:get (:login-form handlers)
               :post (->WrapMiddleware (:login-handler handlers)
                                       wrap-params)}
              wrap-cookies)]
    [""
     (protect
      [["" (:index handlers)]
       ["" (bidi/->ResourcesMaybe {})]]
      :authorizer (new-session-based-request-authorizer :http-session-store sessions)
      :fail-handler (->BidiFailedAuthorizationRedirect (:login-form handlers))
      )]]])

;; Requires :user-registry, :http-session-store
(defrecord MainRoutes []
  component/Lifecycle
  (start [this]
    (let [registry (get-in this [:user-registry])
          sessions (get-in this [:http-session-store])]
      (when-not registry (throw (ex-info "No user registry!" {:this this})))
      (when-not sessions (throw (ex-info "No HTTP session store!" {:this this})))
      (let [opts {:registry registry
                  :sessions sessions}]
        (assoc this :routes (-> opts make-handlers (make-routes opts))))))
  (stop [this] this)

  modular.bidi/BidiRoutesContributor
  (routes [this] (:routes this))
  (context [this] ""))

(defn new-main-routes [] (->MainRoutes))
