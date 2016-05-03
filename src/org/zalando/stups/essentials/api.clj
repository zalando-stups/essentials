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
            [org.zalando.stups.friboo.user :as u]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.essentials.sql :as sql]
            [org.zalando.stups.essentials.external.kio :as kio]
            [io.sarnowski.swagger1st.util.api :refer [throw-error]]
            [ring.util.response :refer :all]
            [clojure.string :as str]
            [clojure.set :as set]))

; define the API component and its dependencies
(def-http-component API "api/essentials-api.yaml" [db])

(def default-http-configuration
  {:http-port 8080})

(defn get-access-token [request]
  (get-in request [:tokeninfo "access_token"]))

(defn require-special-uid
  "Checks wether a given user is configured to be allowed to access this endpoint. Workaround for now."
  [{:keys [configuration tokeninfo]}]
  (let [uids (into #{} (str/split (require-config configuration :allowed-uids) #","))]
    (when-not (contains? uids (get tokeninfo "uid"))
      (log/warn "ACCESS DENIED (unauthorized) because not a special user.")
      (throw-error 403 "Unauthorized"))))

(defn require-write-access
  "Check whether the given resource type id starts with an application id belonging to a team of the user."
  [id {:keys [configuration] :as request}]
  (if-let [app-id (second (re-find #"^([a-z][a-z\-]+[a-z])(:?\..+)?" id))]
    (do
      ; ask kio
      (if-let [app (kio/get-app (require-config configuration :kio-url) app-id (get-access-token request))]
        ; if kio *does* know, verify that teams match
        (u/require-internal-team (:team_id app) request)
        ; if kio does not know this app, fall back to special uids
        (do
          (log/debug "Failed to fetch application %s, falling back to special UIDs" app-id)
          (require-special-uid request))))
      ; do not proceed if what we have does not remotely look like an app id
      ; should actually never be called due to the required pattern in the swagger definition
      (do
        (log/warn "ACCESS DENIED could not extract application id from \"%s\"." id)
        (throw-error 400 "Bad request"))))

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

(defn- load-resource-type
  [resource_type_id db]
  (when-first [row (sql/cmd-read-resource-type {:resource_type_id resource_type_id} {:connection db})]
    (-> row
        strip-prefix
        (update-in [:resource_owners] parse-resource-owners))))

(defn read-resource-types
  "Provides a list of all resource types"
  [_ request db]
  (if (:tokeninfo request)
    (u/require-realms #{"services" "employees"} request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Read all resource types...")
  (->> (sql/cmd-read-resource-types {} {:connection db})
       (map strip-prefix)
       (response)
       (content-type-json)))

(defn read-resource-type
  "Reads detailed information about ine resource type from database"
  [{:keys [resource_type_id]} request db]
  (if (:tokeninfo request)
    (u/require-realms #{"services" "employees"} request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Read resource type '%s'..." resource_type_id)
  (if-let [resource-type (load-resource-type resource_type_id db)]
    (content-type-json (response resource-type))
    (not-found {})))

(defn- valid-resource-owners
  [configuration]
  (set (str/split (require-config configuration :valid-resource-owners) #",")))

(defn- validate-resource-owners
  "Checks the given resource owner list:
   1. An empty list is only permitted, when no resource-owner-scopes exist for that resource type.
   2. The list must only contain valid (well-known) entries."
  [resource-owners resource-type-id db request]
  (let [resource-owners (set resource-owners)]
    (if (empty? resource-owners)
      (let [resource-owner-scopes (filterv :s_is_resource_owner_scope
                                           (sql/cmd-read-scopes {:resource_type_id resource-type-id} {:connection db}))]
        (when-not (empty? resource-owner-scopes)
          (throw-error
            400
            "Cannot remove resource owners from resource type, because it already contains resource-owner-scopes"
            {:resource_type_id resource-type-id :affected_scope_ids (map :s_id resource-owner-scopes)})))
      (let [valid-resource-owners   (-> request :configuration valid-resource-owners)
            unknown-resource-owners (set/difference resource-owners valid-resource-owners)]
        (when-not (empty? unknown-resource-owners)
          (throw-error
            400
            (str "Resource owner list contains invalid entries: " unknown-resource-owners)
            {:invalid_resource_owners unknown-resource-owners :possible_resource_owners valid-resource-owners})))))
  nil)

(defn create-or-update-resource-type
  "Creates or updates a resource type"
  [{:keys [resource_type_id resource_type]} request db]
  (if (:tokeninfo request)
    (require-write-access resource_type_id request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Saving resource type '%s'..." resource_type_id)
  (validate-resource-owners (:resource_owners resource_type) resource_type_id db request)
  (sql/cmd-create-or-update-resource-type!
    {:resource_type_id resource_type_id
     :name             (:name resource_type)
     :description      (:description resource_type)
     :resource_owners  (str/join "," (:resource_owners resource_type))}
    {:connection db})
  (log/info "Saved resource type '%s' with %s" resource_type_id resource_type)
  (response nil))

(defn delete-resource-type
  "Deletes a resource type from the database"
  [{:keys [resource_type_id]} request db]
  (if (:tokeninfo request)
    (require-write-access resource_type_id request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Deleting resource type '%s' ..." resource_type_id)
  (let [deleted (pos? (sql/cmd-delete-resource-type! {:resource_type_id resource_type_id} {:connection db}))]
    (if deleted
      (do (log/info "Deleted resource type '%s'" resource_type_id)
          (response nil))
      (not-found nil))))

(defn read-scopes
  "Reads the scopes of one resource type from database"
  [{:keys [resource_type_id]} request db]
  (if (:tokeninfo request)
    (u/require-realms #{"services" "employees"} request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Read scopes of resource type '%s' ..." resource_type_id)
  (->> (sql/cmd-read-scopes {:resource_type_id resource_type_id} {:connection db})
       (map strip-prefix)
       (response)
       (content-type-json)))

(defn read-scope
  "Read one scope from database"
  [{:keys [resource_type_id scope_id]} request db]
  (if (:tokeninfo request)
    (u/require-realms #{"services" "employees"} request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Read scope '%s' of resource type '%s' ..." scope_id resource_type_id)
  (->> (sql/cmd-read-scope {:resource_type_id resource_type_id
                            :scope_id         scope_id} {:connection db})
       (map strip-prefix)
       (single-response)
       (content-type-json)))

(defn prepare-scope-data
  "prepares data to be passed to sql"
  [{:keys [defaults scope-keys scope-ids]}]
  (merge-with #(or %2 %1) defaults scope-keys scope-ids))

(defn create-or-update-scope
  "Creates or updates a scope"
  [{:keys [resource_type_id scope_id scope]} request db]
  (if (:tokeninfo request)
    (require-write-access resource_type_id request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Saving scope '%s' of resource type '%s'..." scope_id resource_type_id)
  (if-let [resource-type (load-resource-type resource_type_id db)]
    (do (when (and (:is_resource_owner_scope scope)
                   (empty? (:resource_owners resource-type)))
          (throw-error
            400
            "A resource-owner-scope requires its resource type to have at least one resource owner"
            {:resource_type_id resource_type_id
             :scope_id scope_id}))
        (let [defaults {:criticality_level 2
                        :description       nil
                        :user_information  nil
                        :summary           nil}
              scope-keys (select-keys scope [:summary
                                             :description
                                             :user_information
                                             :is_resource_owner_scope
                                             :criticality_level])
              scope-ids {:resource_type_id resource_type_id
                         :scope_id         scope_id}]
        (sql/cmd-create-or-update-scope!
          (prepare-scope-data {:defaults {:criticality_level 2}
                               :scope-keys (select-keys scope [:summary
                                                               :description
                                                               :user_information
                                                               :is_resource_owner_scope
                                                               :criticality_level])
                               :scope-ids {:resource_type_id resource_type_id
                                           :scope_id         scope_id}})
          {:connection db})
        (log/info "Saved scope '%s' of resource type '%s' with %s" scope_id resource_type_id scope)
        (response nil))
    (do (log/debug "Resource type '%s' not found" resource_type_id)
        (not-found nil))))

(defn delete-scope
  "Deletes a scope"
  [{:keys [resource_type_id scope_id]} request db]
  (if (:tokeninfo request)
    (require-write-access resource_type_id request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set HTTP_TOKENINFO_URL to enable full validation"))
  (log/debug "Deleting scope '%s' of resource type '%s'..." scope_id resource_type_id)
  (if (load-resource-type resource_type_id db)
    (do (sql/cmd-delete-scope! {:resource_type_id resource_type_id :scope_id scope_id}
                               {:connection db})
        (log/info "Deleted scope '%s' of resource type '%s'" scope_id resource_type_id)
        (response nil))
    (do (log/debug "Resource type '%s' not found" resource_type_id)
        (not-found nil))))
