(ns org.zalando.stups.essentials.core-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer :all]
            [org.zalando.stups.essentials.core :refer :all]
            [org.zalando.stups.friboo.test-utils :as u]
            [com.stuartsierra.component :as component]
            [clj-http.client :as http]))

(deftest wrap-midje-facts

  (facts "about run"
    (let [port (u/get-free-port)
          config {:http-port port
                  :mgmt-http-port (u/get-free-port)
                  :db-subname "//localhost:5432/postgres"}
          system (run config)]
      (try
        (facts "works"
          (http/get (str "http://localhost:" port "/resource-types")) => (contains {:status 200}))
        (finally
          (component/stop system)))))

  )
