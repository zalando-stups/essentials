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
  (:require [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.user :as u]
            [org.zalando.stups.friboo.zalando-specific.auth :as auth]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.essentials.sql :as sql]
            [org.zalando.stups.essentials.external.kio :as kio]
            [io.sarnowski.swagger1st.util.api :refer [throw-error]]
            [ring.util.response :refer :all]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn parse-csv-set [items-str]
  (set (str/split items-str #",")))

(defn require-special-uid
  "Checks wether a given user is configured to be allowed to access this endpoint. Workaround for now."
  [{:keys [configuration]} {:strs [uid]}]
  (let [uids (parse-csv-set (require-config configuration :allowed-uids))]
    (when-not (contains? uids uid)
      (log/warn "ACCESS DENIED (unauthorized) because not a special user.")
      (throw-error 403 "Unauthorized"))))

(defn extract-app-id
  "'application.write_all' -> 'application'"
  [resource-type-id]
  (second (re-find #"^([a-z][a-z\-]+[a-z])(:?\..+)?" resource-type-id)))

(defn require-write-access
  "Check whether the given resource type id starts with an application id belonging to a team of the user."
  [{:as this :keys [configuration auth]} resource-type-id {:keys [tokeninfo]}]
  (if-not tokeninfo
    (log/warn "Could not validate authorization due to missing tokeninfo. Set TOKENINFO_URL to enable full validation")
    (if-let [app-id (extract-app-id resource-type-id)]
      (do
        ; ask kio
        (if-let [app (kio/get-app (require-config configuration :kio-url) app-id (get tokeninfo "access_token"))]
          ; if kio *does* know, ask magnificent if it's ok
          (auth/require-auth auth tokeninfo {:team (:team_id app)})
          ; if kio does not know this app, fall back to special uids
          (do
            (log/debug "Failed to fetch application %s, falling back to special UIDs" app-id)
            (require-special-uid this tokeninfo))))
      ; do not proceed if what we have does not remotely look like an app id
      ; should actually never be called due to the required pattern in the swagger definition
      (do
        (log/warn "ACCESS DENIED could not extract application id from \"%s\"." resource-type-id)
        (throw-error 400 "Bad request")))))

(defn strip-prefix
  "Removes the database field prefix."
  [m]
  (let [prefix-pattern #"[a-z]+_(.+)"
        remove-prefix  (fn [k]
                         (->> k name (re-find prefix-pattern) second keyword))]
    (into {} (map
               (fn [[k v]] [(remove-prefix k) v])
               m))))

(defn parse-resource-owners [string]
  (filterv #(not (str/blank? %)) (str/split string #",")))

(defn load-resource-type
  [{:keys [db]} resource-type-id]
  (when-first [row (sql/cmd-read-resource-type {:resource_type_id resource-type-id} {:connection db})]
    (-> row
        strip-prefix
        (update-in [:resource_owners] parse-resource-owners))))

(defn require-realms [{:keys [configuration]} request]
  (if (:tokeninfo request)
    (u/require-realms (parse-csv-set (:allowed-realms configuration "services,employees")) request)
    (log/warn "Could not validate authorization due to missing tokeninfo. Set TOKENINFO_URL to enable full validation")))

(defn read-resource-types
  "Provides a list of all resource types"
  [{:as this :keys [db]} _ request]
  (require-realms this request)
  (log/debug "Read all resource types...")
  (->> (sql/cmd-read-resource-types {} {:connection db})
       (map strip-prefix)
       (response)))

(defn read-resource-type
  "Reads detailed information about ine resource type from database"
  [this {:keys [resource_type_id]} request]
  (require-realms this request)
  (log/debug "Read resource type '%s'..." resource_type_id)
  (if-let [resource-type (load-resource-type this resource_type_id)]
    (response resource-type)
    (not-found {})))

(defn validate-resource-owners
  "Checks the given resource owner list:
   1. An empty list is only permitted, when no resource-owner-scopes exist for that resource type.
   2. The list must only contain valid (well-known) entries."
  [{:keys [db configuration]} resource-owners resource-type-id]
  (let [resource-owners (set resource-owners)]
    (if (empty? resource-owners)
      (let [resource-owner-scopes (filterv :s_is_resource_owner_scope
                                           (sql/cmd-read-scopes {:resource_type_id resource-type-id} {:connection db}))]
        (when-not (empty? resource-owner-scopes)
          (throw-error
            400
            "Cannot remove resource owners from resource type, because it already contains resource-owner-scopes"
            {:resource_type_id resource-type-id :affected_scope_ids (map :s_id resource-owner-scopes)})))
      (let [valid-resource-owners   (parse-csv-set (require-config configuration :valid-resource-owners))
            unknown-resource-owners (set/difference resource-owners valid-resource-owners)]
        (when-not (empty? unknown-resource-owners)
          (throw-error
            400
            (str "Resource owner list contains invalid entries: " unknown-resource-owners)
            {:invalid_resource_owners unknown-resource-owners :possible_resource_owners valid-resource-owners})))))
  nil)

(defn create-or-update-resource-type
  "Creates or updates a resource type"
  [{:as this :keys [db]} {:keys [resource_type_id resource_type]} request]
  (require-write-access this resource_type_id request)
  (log/debug "Saving resource type '%s'..." resource_type_id)
  (validate-resource-owners this (:resource_owners resource_type) resource_type_id)
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
  [{:as this :keys [db]} {:keys [resource_type_id]} request]
  (require-write-access this resource_type_id request)
  (log/debug "Deleting resource type '%s' ..." resource_type_id)
  (let [deleted (pos? (sql/cmd-delete-resource-type! {:resource_type_id resource_type_id} {:connection db}))]
    (if deleted
      (do (log/info "Deleted resource type '%s'" resource_type_id)
          (response nil))
      (not-found nil))))

(defn read-scopes
  "Reads the scopes of one resource type from database"
  [{:as this :keys [db]} {:keys [resource_type_id]} request]
  (require-realms this request)
  (log/debug "Read scopes of resource type '%s' ..." resource_type_id)
  (->> (sql/cmd-read-scopes {:resource_type_id resource_type_id} {:connection db})
       (map strip-prefix)
       (response)))

(defn read-scope
  "Read one scope from database"
  [{:as this :keys [db]} {:keys [resource_type_id scope_id]} request]
  (require-realms this request)
  (log/debug "Read scope '%s' of resource type '%s' ..." scope_id resource_type_id)
  (->> (sql/cmd-read-scope {:resource_type_id resource_type_id
                            :scope_id         scope_id} {:connection db})
       (map strip-prefix)
       (single-response)))

(defn create-or-update-scope
  "Creates or updates a scope"
  [{:as this :keys [db]} {:keys [resource_type_id scope_id scope]} request]
  (require-write-access this resource_type_id request)
  (log/debug "Saving scope '%s' of resource type '%s'..." scope_id resource_type_id)
  (if-let [resource-type (load-resource-type this resource_type_id)]
    (do
      (when (and (:is_resource_owner_scope scope)
                 (empty? (:resource_owners resource-type)))
        (throw-error
          400
          "A resource-owner-scope requires its resource type to have at least one resource owner"
          {:resource_type_id resource_type_id
           :scope_id         scope_id}))
      (sql/cmd-create-or-update-scope!
        {:scope_id                scope_id
         :resource_type_id        resource_type_id
         :summary                 (:summary scope)
         :is_resource_owner_scope (or (:is_resource_owner_scope scope) false)
         :description             (:description scope)
         :user_information        (:user_information scope)
         :criticality_level       (or (:criticality_level scope) 2)}
        {:connection db})
      (log/info "Saved scope '%s' of resource type '%s' with %s" scope_id resource_type_id scope)
      (response nil))
    (do (log/debug "Resource type '%s' not found" resource_type_id)
        (not-found nil))))

(defn delete-scope
  "Deletes a scope"
  [{:as this :keys [db]} {:keys [resource_type_id scope_id]} request]
  (require-write-access this resource_type_id request)
  (log/debug "Deleting scope '%s' of resource type '%s'..." scope_id resource_type_id)
  (if (load-resource-type this resource_type_id)
    (do (sql/cmd-delete-scope! {:resource_type_id resource_type_id :scope_id scope_id}
                               {:connection db})
        (log/info "Deleted scope '%s' of resource type '%s'" scope_id resource_type_id)
        (response nil))
    (do (log/debug "Resource type '%s' not found" resource_type_id)
        (not-found nil))))
