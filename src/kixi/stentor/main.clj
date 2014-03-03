(ns kixi.stentor.main
  (:gen-class))

(defn -main [& args]
  (eval '(do (require 'kixi.stentor.system)
             (require 'com.stuartsierra.component)
             (com.stuartsierra.component/start (kixi.stentor.system/new-system)))))
