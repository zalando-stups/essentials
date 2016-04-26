(ns org.zalando.stups.essentials.external.kio
  (:require [clj-http.client :as http]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.system.oauth2 :as oauth]))

(defn- make-options
  [tokens]
  {:headers {:authorization (str "Bearer " (oauth/access-token :kio tokens))}
   :accept :json
   :as :json})

(defn get-app
  "Fetches application with this id from kio, returns nil if it does not exist"
  [kio-url id tokens]
  {:pre [(not (clojure.string/blank? id))]}
  (let [options (make-options tokens)]
    (try
      (:body (http/get (str kio-url "/apps/" id) options))
      (catch Exception _
        nil))))
