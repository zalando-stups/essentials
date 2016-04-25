(ns org.zalando.stups.essentials.external.kio
  (:require [clj-http.client :as http]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.system.oauth2 :as oauth]))

(defn- make-auth-header
  [tokens]
  {:authorization (str "Bearer " (oauth/access-token :kio tokens))})

(defn get-app
  "Fetches application with this id from kio, returns nil if it does not exist"
  [kio-url id tokens]
  {:pre (not (clojure.string/blank? id))}
  (let [header (make-auth-header tokens)]
    (try
      (-> (http/get (str kio-url "/" id) header)
          :body)
      (catch Exception ex
        (log/warn "Could not find application %s in Kio. %s" id ex)
        nil))))
