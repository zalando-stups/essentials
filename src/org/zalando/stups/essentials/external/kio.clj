(ns org.zalando.stups.essentials.external.kio
  (:require [clj-http.client :as http]
            [slingshot.slingshot :refer [try+]]
            [com.netflix.hystrix.core :as hystrix]))

(hystrix/defcommand get-app
  "Fetches application with this id from kio, returns nil if it does not exist"
  [kio-url id token]
  {:pre [(not (clojure.string/blank? kio-url))
         (not (clojure.string/blank? id))
         (not (clojure.string/blank? token))]}
  (try+
    (:body (http/get (str kio-url "/apps/" id) {:oauth-token token
                                                :accept :json
                                                :as :json}))
    (catch [:status 404] _
      nil)))
