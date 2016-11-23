;; # Utilities
;;
(ns org.zalando.stups.essentials.utils)

(defn slurp-if-exists [file]
  (when (.exists (clojure.java.io/as-file file))
    (slurp file)))

(defn load-dev-config
  ([]
   (load-dev-config "./dev-config.edn"))
  ([file]
   (clojure.edn/read-string (slurp-if-exists file))))
