(ns org.zalando.stups.essentials.core-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer :all]
            [org.zalando.stups.essentials.core :refer :all]
            [org.zalando.stups.essentials.utils :as u]
            [org.zalando.stups.essentials.test-utils :as tu]
            [com.stuartsierra.component :as component]
            [clj-http.client :as http]))

(deftest test-core-system

  (facts "about run"
    (let [dev-config  (u/load-dev-config)
          test-config (merge {:http-port      (tu/get-free-port)
                              :mgmt-http-port (tu/get-free-port)}
                             dev-config)
          system      (run test-config)
          port        (-> system :http :configuration :port)]
      (try
        (facts "works"
          (http/get (str "http://localhost:" port "/resource-types")) => (contains {:status 200}))
        (facts "can create resource type"
          (http/put (str "http://localhost:" port "/resource-types/hello")
                    {:content-type :json :form-params {:name "Hello" :resource_owners []}})
          => (contains {:status 200}))
        (finally
          (component/stop system)))))

  )
