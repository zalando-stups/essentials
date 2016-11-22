;; # Utilities
;;
(ns org.zalando.stups.essentials.utils)

(defn implementation-version
  "Takes version from MANIFEST.MF, works only from uberjar, otherwise returns nil."
  []
  (some-> "org.zalando.automata.cloud_kraken.core" Package/getPackage .getImplementationVersion))

(defn slurp-if-exists [file]
  (when (.exists (clojure.java.io/as-file file))
    (slurp file)))

(defn load-dev-config
  ([]
   (load-dev-config "./dev-config.edn"))
  ([file]
   (clojure.edn/read-string (slurp-if-exists file))))
