(ns org.zalando.stups.essentials.t-api
  (:require
    [midje.sweet :refer :all]
    [clojure.test :refer :all]
    [org.zalando.stups.essentials.sql :as sql]
    [org.zalando.stups.essentials.api :as api]))


(facts "about scope creation"

       (fact "prepare-scope-data correctly prepares (merges) scope-data for create-or-update-scope!"
             (api/prepare-scope-data ..scope-data..) => {..key1.. ..value1..
                                                         ..key2.. nil
                                                         ..key3.. ..value3..
                                                         ..key4.. ..value4..
                                                         ..key5.. ..value5..}
             (provided
               ..scope-data.. =contains=> {:defaults {..key1.. ..value1..
                                                      ..key2.. nil
                                                      ..key3.. nil}
                                           :scope-keys {..key3.. ..value3..
                                                        ..key4.. ..value4..}
                                           :scope-ids {..key5.. ..value5..}}))

       (fact "create-or-update-scope creates a scope"
             (api/create-or-update-scope ..params.. ..request.. ..db..) => (contains {:status 200})
             (provided
               ..params.. =contains=> {:resource_type_id ..resource-type-id.. :scope_id ..scope-id.. :scope ..scope..}
               ..request.. =contains=> {:tokeninfo ..tokeninfo..}
               ..resource-type.. =contains=> {:resource_owners ..resource-owners..}
               (#'api/load-resource-type ..resource-type-id.. ..db..) => ..resource-type.. :times 1
               (api/prepare-scope-data (contains {:defaults anything :scope-keys anything :scope-ids anything})) => ..prepared-scope-data.. :times 1
               (sql/create-or-update-scope! ..prepared-scope-data..  {:connection ..db..}) => anything :times 1
               (api/require-write-access ..resource-type-id.. ..request..) => anything :times 1)))
