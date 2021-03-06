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

(ns user
  (:require
   [com.stuartsierra.component :as component]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [kixi.stentor.system :refer (new-system)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [modular :refer (system)]
   [cylon.core :as cylon]))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (new-system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start)
  nil)

(defn reset []
  (stop)
  (refresh :after 'user/go)
  nil)

(defn add-user!
  "Create a new user in the protection system. For existing user, the
  given password will replace the old one, so this function can be used
  for resetting passwords too."
  [uid pw]
  (cylon/add-user!
   (-> system :protection-system :user-password-authorizer)
   uid pw))
