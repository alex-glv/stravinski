(defproject stravinski "0.1.0-SNAPSHOT"
  :description ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [riemann-clojure-client "0.3.2"]
                 [twitter-api "0.7.8"]
                 [cheshire "5.5.0"]
                 [ring "1.4.0"]
                 [enlive "1.1.5"]]
  :main ^:skip-aot stravinski.core
  :target-path "target/%s"
  :profiles {:user
             {:dependencies [[org.clojure/tools.nrepl "0.2.10"]]
              :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]
                        [refactor-nrepl "1.2.0-SNAPSHOT"]]}

             :uberjar {:aot :all}})

