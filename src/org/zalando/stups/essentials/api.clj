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
            [io.sarnowski.swagger1st.util.api :refer [throw-error]]
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

(defn- parse-resource-owners [string]
  (filterv #(not (str/blank? %)) (str/split string #",")))

(defn- resource-type-from-db [row]
  (-> row
      strip-prefix
      (update-in [:resource_owners] parse-resource-owners)))

(defn- load-resource-type
  [resource_type_id db]
  (when-first [row (sql/read-resource-type {:resource_type_id resource_type_id} {:connection db})]
    (resource-type-from-db row)))

(defn read-resource-types
  "Provides a list of all resource types"
  [_ _ db]
  (log/debug "Read all resource types...")
  (->> (sql/read-resource-types {} {:connection db})
       (map strip-prefix)
       (response)
       (content-type-json)))

(defn read-resource-type
  "Reads detailed information about ine resource type from database"
  [{:keys [resource_type_id]} _ db]
  (log/debug "Read resource type '%s'..." resource_type_id)
  (if-let [resource-type (load-resource-type resource_type_id db)]
    (content-type-json (response resource-type))
    (not-found {})))

(defn create-or-update-resource-type
  "Creates or updates a resource type"
  [{:keys [resource_type_id resource_type]} _ db]
  (log/debug "Saving resource type '%s'..." resource_type_id)
  (when (and (empty? (:resource_owners resource_type))
             (some :s_is_resource_owner_scope (sql/read-scopes {:resource_type_id resource_type_id} {:connection db})))
    (throw-error
      400
      "Cannot remove resource owners from resource type, because it already contains resource-owner-scopes"
      (format "Resource type: '%s'" resource_type_id)))
  (sql/create-or-update-resource-type!
    {:resource_type_id resource_type_id
     :name             (:name resource_type)
     :description      (:description resource_type)
     :resource_owners  (str/join "," (:resource_owners resource_type))}
    {:connection db})
  (log/info "Saved resource type '%s' with %s" resource_type_id resource_type)
  (response nil))

(defn delete-resource-type
  "Deletes a resource type from the database"
  [{:keys [resource_type_id]} _ db]
  (log/debug "Deleting resource type '%s' ..." resource_type_id)
  (let [deleted (pos? (sql/delete-resource-type! {:resource_type_id resource_type_id} {:connection db}))]
    (if deleted
      (do (log/info "Deleted resource type '%s'" resource_type_id)
          (response nil))
      (not-found nil))))

(defn read-scopes
  "Reads the scopes of one resource type from database"
  [{:keys [resource_type_id]} _ db]
  (log/debug "Read scopes of resource type '%s' ..." resource_type_id)
  (->> (sql/read-scopes {:resource_type_id resource_type_id} {:connection db})
       (map strip-prefix)
       (response)
       (content-type-json)))

(defn read-scope
  "Read one scope from database"
  [{:keys [resource_type_id scope_id]} _ db]
  (log/debug "Read scope '%s' of resource type '%s' ..." scope_id resource_type_id)
  (->> (sql/read-scope {:resource_type_id resource_type_id
                        :scope_id         scope_id} {:connection db})
       (map strip-prefix)
       (single-response)
       (content-type-json)))

(defn create-or-update-scope
  "Creates or updates a scope"
  [{:keys [resource_type_id scope_id scope]} _ db]
  (log/debug "Saving scope '%s' of resource type '%s'..." scope_id resource_type_id)
  (if-let [resource-type (load-resource-type resource_type_id db)]
    (do (when (and (:is_resource_owner_scope scope)
                   (empty? (:resource_owners resource-type)))
          (throw-error
            400
            "A resource-owner-scope requires its resource type to have at least one resource owner"
            (format "Resource type: '%s'" resource_type_id)
            (format "Scope: '%s'" scope_id)))
        (sql/create-or-update-scope!
          {:resource_type_id        resource_type_id
           :scope_id                scope_id
           :summary                 (:summary scope)
           :description             (:description scope)
           :user_information        (:user_information scope)
           :criticality_level       (:criticality_level scope)
           :is_resource_owner_scope (:is_resource_owner_scope scope)}
          {:connection db})
        (log/info "Saved scope '%s' of resource type '%s' with %s" scope_id resource_type_id scope)
        (response nil))
    (do (log/debug "Resource type '%s' not found" resource_type_id)
        (not-found nil))))

(defn delete-scope
  "Deletes a scope"
  [{:keys [resource_type_id scope_id]} _ db]
  (log/debug "Deleting scope '%s' of resource type '%s'..." scope_id resource_type_id)
  (if (load-resource-type resource_type_id db)
    (do (sql/delete-scope! {:resource_type_id resource_type_id :scope_id scope_id}
                           {:connection db})
        (log/info "Deleted scope '%s' of resource type '%s'" scope_id resource_type_id)
        (response nil))
    (do (log/debug "Resource type '%s' not found" resource_type_id)
        (not-found nil))))
