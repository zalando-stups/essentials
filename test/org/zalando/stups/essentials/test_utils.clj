(ns org.zalando.stups.essentials.test-utils
  (:require [clojure.java.jdbc :as jdbc]
            [org.zalando.stups.friboo.system.db :as db]
            [com.stuartsierra.component :as component]))

(defn wipe-db
  [db]
  (jdbc/delete! db :scope ["s_id IS NOT NULL"])
  (jdbc/delete! db :resource_type ["rt_id IS NOT NULL"]))

(def test-db-config
  {:classname    "org.postgresql.Driver"
   :subprotocol  "postgresql"
   :subname      "//localhost:5432/postgres"
   :user         "postgres"
   :password     "postgres"
   :init-sql     "SET search_path TO ze_data, public"
   :auto-migration? true})

(defmacro with-db [[db] & body]
  `(let [~db (component/start (db/map->DB {:configuration test-db-config}))]
     (try
       ~@body
       (finally
         (component/stop ~db)))))
