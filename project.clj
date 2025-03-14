(defproject clojure-ladder "0.1.0-SNAPSHOT"
  :description "A Ladder Logic Simulator implemented in Clojure"
  :url "http://example.com/clojure-ladder"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/spec.alpha "0.3.218"]
                 [quil "4.3.1563"]
                 [org.clojure/core.async "1.6.673"]]
  :main ^:skip-aot clojure-ladder.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
