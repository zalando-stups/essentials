(ns org.zalando.stups.essentials.test-utils
  (:require [clojure.java.jdbc :as jdbc]
            [org.zalando.stups.friboo.system.db :as db]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.essentials.core :as core]
            [org.zalando.stups.friboo.zalando-specific.config :as config]
            [org.zalando.stups.essentials.utils :as u])
  (:import (java.net ServerSocket)))

(defn wipe-db
  [db]
  (jdbc/delete! db :scope ["s_id IS NOT NULL"])
  (jdbc/delete! db :resource_type ["rt_id IS NOT NULL"]))

(defmacro with-db [[db] & body]
  `(let [dev-config# (u/load-dev-config)
         config# (config/load-config (merge core/default-db-config dev-config#) [:db])
         ~db (component/start (db/map->DB {:configuration (:db config#)}))]
     (try
       ~@body
       (finally
         (component/stop ~db)))))

(defn get-free-port []
  (let [sock (ServerSocket. 0)]
    (try
      (.getLocalPort sock)
      (finally
        (.close sock)))))
