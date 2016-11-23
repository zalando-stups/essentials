(ns org.zalando.stups.essentials.api-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer :all]
            [org.zalando.stups.essentials.api :refer :all]
            [org.zalando.stups.essentials.test-utils :refer :all]
            [clj-http.client :as http]
            [org.zalando.stups.friboo.zalando-internal.auth :as auth]
            [org.zalando.stups.essentials.sql :as sql])
  (:import (clojure.lang ExceptionInfo)))

(deftest wrap-midje-facts

  (facts "about extract-app-id"
    (extract-app-id "user-filtering.module") => "user-filtering"
    (extract-app-id "user-procurement") => "user-procurement")

  (facts "about strip-prefix"
    (strip-prefix {:rt_id 1 :s_summary "aaa"})
    => {:id 1, :summary "aaa"})

  (facts "about parse-csv-set"
    (parse-csv-set "a,b,a") => #{"a" "b"})

  (facts "about require-special-uid"
    (require-special-uid {:configuration {:allowed-uids "abob,cdale"}} {"uid" "abob"}) => nil
    (require-special-uid {:configuration {:allowed-uids "abob,cdale"}} {"uid" "mjackson"}) => (throws ExceptionInfo))

  (facts "about require-write-access"
    (let [tokeninfo {"access_token" "token"
                     "uid"          "abob"}
          this      {:configuration {:kio-url      "kio-url"
                                     :allowed-uids "abob,cdale"}
                     :auth          ..auth..}]
      (fact "for scopes that are application ids"
        (require-write-access this "zmon" {:tokeninfo tokeninfo})
        => nil
        (provided
          (http/get "kio-url/apps/zmon" (contains {:oauth-token "token"})) => {:status 200 :body {:team_id "team-zmon"}}
          (auth/require-auth ..auth.. tokeninfo {:team "team-zmon"}) => nil))
      (fact "for other scopes"
        (require-write-access this "application" {:tokeninfo tokeninfo})
        => nil
        (provided
          (http/get "kio-url/apps/application" (contains {:oauth-token "token"})) => {:status 404}))
      (fact "When tokeninfo-url is not set, do nothing"
        (require-write-access {} "foo" {}) => nil
        (provided
          (http/get anything anything) => nil :times 0))
      (fact "When tokeninfo-url is not set, do nothing"
        (require-write-access this "" {:tokeninfo tokeninfo}) => (throws ExceptionInfo)
        (provided
          (http/get anything anything) => nil :times 0))))

  (facts "about require-realms"
    (require-realms {:configuration {:allowed-realms "foos"}} {:tokeninfo {"realm" "foos"}})
    => anything
    (fact "When there is no tokeninfo in the request, do nothing"
      (require-realms {:configuration {:allowed-realms "foos"}} {})
      => nil))

  (facts "about validate-resource-owners"
    "When resource owners list is not empty, just check it against the list"
    (validate-resource-owners {:configuration {:valid-resource-owners "foos,bars"}}
                              ["bros"]
                              anything) => (throws ExceptionInfo)
    (fact "When the list is empty, check in the database"
      (validate-resource-owners {}
                                []
                                "foo") => (throws ExceptionInfo)
      (provided
        (sql/cmd-read-scopes {:resource_type_id "foo"} anything) => [{:s_is_resource_owner_scope true}])))

  (facts "Component test"
    (with-db [db]
      (let [this {:db            db
                  :auth          ..auth..
                  :configuration {:allowed-realms        "bros"
                                  :allowed-uids          "abob,cdale"
                                  :valid-resource-owners "bros"
                                  :kio-url               "kio-url"}}]
        (wipe-db db)

        (fact "Unallowed realms not allowed"
          (read-resource-types this {} {:tokeninfo {"realm" "robots"}})
          => (throws ExceptionInfo))

        (fact "Allowed realm, no resource types in the DB yet"
          (read-resource-types this {} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 200 :body []}))

        (fact "Can create resource type"
          (create-or-update-resource-type this
                                          {:resource_type_id "application"
                                           :resource_type    {:name            "Application name"
                                                              :description     "Application description"
                                                              :resource_owners []}}
                                          {:tokeninfo {"access_token" "token"
                                                       "uid"          "abob"
                                                       "realm"        "bros"}})
          => (contains {:status 200})
          (provided
            (http/get "kio-url/apps/application" (contains {:oauth-token "token"})) => {:status 404}))

        (fact "Resource type is in the DB now"
          (read-resource-types this {} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 200 :body [{:id "application", :name "Application name"}]}))

        (fact "Can get resource type by id"
          (read-resource-type this {:resource_type_id "application"} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 200 :body {:id              "application"
                                           :name            "Application name"
                                           :description     "Application description"
                                           :resource_owners []}}))

        (fact "No scopes for the resource type yet"
          (read-scopes this {:resource_type_id "application"} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 200 :body []}))

        (fact "Can create a scope for the resource type (use a non-special uid to check how require-auth is called)"
          (create-or-update-scope this
                                  {:resource_type_id "application"
                                   :scope_id         "write"
                                   :scope            {:summary "Allow write"}}
                                  {:tokeninfo {"access_token" "token"
                                               "uid"          "mjackson"
                                               "realm"        "bros"}})
          => (contains {:status 200})
          (provided
            (http/get "kio-url/apps/application" (contains {:oauth-token "token"}))
            => {:status 200 :body {:team_id "broforce"}}
            (auth/require-auth ..auth..
                               {"uid" "mjackson" "access_token" "token" "realm" "bros"}
                               {:team "broforce"})
            => nil))

        (fact "Created scope is visible"
          (read-scopes this {:resource_type_id "application"} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 200 :body [{:criticality_level       2
                                            :description             nil
                                            :id                      "write"
                                            :is_resource_owner_scope false
                                            :summary                 "Allow write"
                                            :user_information        nil}]}))

        (fact "Can read scope by ID"
          (read-scope this {:resource_type_id "application" :scope_id "write"} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 200 :body {:criticality_level       2
                                           :description             nil
                                           :id                      "write"
                                           :is_resource_owner_scope false
                                           :summary                 "Allow write"
                                           :user_information        nil}}))

        (fact "Cannot create resource owner scope when resource type has no resource owners"
          (create-or-update-scope this
                                  {:resource_type_id "application"
                                   :scope_id         "write_all"
                                   :scope            {:summary "Allow write"
                                                      :is_resource_owner_scope true}}
                                  {:tokeninfo {"access_token" "token"
                                               "uid"          "abob"
                                               "realm"        "bros"}})
          => (throws ExceptionInfo)
          (provided
            (http/get "kio-url/apps/application" (contains {:oauth-token "token"}))
            => {:status 200 :body {:team_id "broforce"}}))

        (fact "Cannot create scope in a nonfound resource type"
          (create-or-update-scope this
                                  {:resource_type_id "foo"
                                   :scope_id         "write_all"
                                   :scope            {:summary "Allow write"}}
                                  {:tokeninfo {"access_token" "token"
                                               "uid"          "abob"
                                               "realm"        "bros"}})
          => (contains {:status 404})
          (provided
            (http/get "kio-url/apps/foo" (contains {:oauth-token "token"}))
            => {:status 200 :body {:team_id "broforce"}}))

        (fact "Can delete scope"
          (delete-scope this {:resource_type_id "application" :scope_id "write"} {:tokeninfo {"access_token" "token"
                                                                                              "uid"          "abob"
                                                                                              "realm"        "bros"}})
          => (contains {:status 200})
          (provided
            (http/get "kio-url/apps/application" (contains {:oauth-token "token"})) => {:status 404}))

        (fact "Deleting a scope from unfound resource yields 404"
          (delete-scope this {:resource_type_id "bar" :scope_id "write"} {:tokeninfo {"access_token" "token"
                                                                                      "uid"          "abob"
                                                                                      "realm"        "bros"}})
          => (contains {:status 404})
          (provided
            (http/get "kio-url/apps/bar" (contains {:oauth-token "token"})) => {:status 404}))

        (fact "Deleted scope is gone"
          (read-scope this {:resource_type_id "application" :scope_id "write"} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 404}))

        (fact "Can delete resource type"
          (delete-resource-type this {:resource_type_id "application"} {:tokeninfo {"access_token" "token"
                                                                                    "uid"          "abob"
                                                                                    "realm"        "bros"}})
          => (contains {:status 200})
          (provided
            (http/get "kio-url/apps/application" (contains {:oauth-token "token"})) => {:status 404}))

        (fact "Deleting unfound resource type yields 404"
          (delete-resource-type this {:resource_type_id "application"} {:tokeninfo {"access_token" "token"
                                                                                    "uid"          "abob"
                                                                                    "realm"        "bros"}})
          => (contains {:status 404})
          (provided
            (http/get "kio-url/apps/application" (contains {:oauth-token "token"})) => {:status 404}))

        (fact "Resource type is gone"
          (read-resource-type this {:resource_type_id "application"} {:tokeninfo {"realm" "bros"}})
          => (contains {:status 404})))
      ))

  )
