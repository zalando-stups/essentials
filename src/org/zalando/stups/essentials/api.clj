; Copyright 2015 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.essentials.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.essentials.sql :as sql]
            [ring.util.response :refer :all]
            [clojure.data.json :refer [JSONWriter]]
            [clojure.string :as str]))

; define the API component and its dependencies
(def-http-component API "api/essentials-api.yaml" [db])

(def default-http-configuration
  {:http-port 8080})

(defn- strip-prefix
  "Removes the database field prefix."
  [m]
  (let [prefix-pattern #"[a-z]+_(.+)"
        remove-prefix (fn [k]
                        (->> k name (re-find prefix-pattern) second keyword))]
    (into {} (map
               (fn [[k v]] [(remove-prefix k) v])
               m))))

(defn read-resource-types
  "Provides a list of all resource types"
  [_ _ db]
  (log/debug "Read all resource types...")
  (-> (sql/read-resource-types {} {:connection db})
      (map #(strip-prefix %))
      (response)
      (content-type-json)))

(defn read-resource-type
  "Reads detailed information about ine resource type from database"
  [{:keys [resource_type_id]} _ db]
  (log/debug "Read resource type '%s'..." resource_type_id)
  (-> (sql/read-resource-type {:resource_type_id resource_type_id}
                              {:connection db})
      (map #(strip-prefix %))
      (map #(assoc % :resource_owners (str/split (:resource_owners %) #",")))
      (single-response)
      (content-type-json)))

(defn create-or-update-resource-type
  "Creates or updates a resource type"
  [{:keys [resource_type_id resource_type]} _ db]
  (log/debug "Saving resource type '%s'..." resource_type_id)
  (sql/create-or-update-resource-type!
    {:resource_type_id resource_type_id
     :name             (:name resource_type)
     :description      (:description resource_type)
     :resource_owners  (str/join "," (:resource_owners resource_type))}
    {db})
  (log/info "Saved resource type '%s' with %s" resource_type_id resource_type)
  (response nil))

(defn delete-resource-type
  "Deletes a resource type from the database"
  [{:keys [resource_type_id]} _ db]
  (log/debug "Deleting resource type '%s' ..." resource_type_id)
  (let [deleted (> (sql/delete-resource_type! {:resource_type_id resource_type_id} {:connection db})
                   0)]
    (if deleted
      (do (log/info "Deleted resource type '%s'" resource_type_id)
          (response nil))
      (not-found nil))))

(defn noop []
  (response nil))

