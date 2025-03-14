(ns clojure-ladder.main
  (:gen-class)
  (:require [clojure-ladder.core :as core]
            [clojure-ladder.simulator :as simulator]
            [clojure-ladder.io :as io]
            [clojure-ladder.plc :as plc]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]))

;; ---- Command Line Arguments ----

(def cli-options
  [["-p" "--port PORT" "Port for web interface"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be a number between 0 and 65536"]]
   
   ["-m" "--modbus-port PORT" "Port for Modbus TCP server"
    :default 502
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be a number between 0 and 65536"]]
   
   ["-f" "--file FILE" "Ladder logic program file to load"]
   
   ["-r" "--run" "Start in run mode"]
   
   ["-h" "--help" "Show this help"]])

(defn usage [options-summary]
  (->> ["ClojureLadder - A Ladder Logic Simulator in Clojure"
        ""
        "Usage: java -jar clojure-ladder.jar [options]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  java -jar clojure-ladder.jar -f myprogram.edn -r"
        "  java -jar clojure-ladder.jar -p 8888 -m 5502"]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors) :ok? false}
      
      :else ; valid options
      {:options options :arguments arguments})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ---- Web Interface ----

;; Simplified Web Server for monitoring/controlling the PLC
;; In a real implementation, this would use Ring/Compojure/other web frameworks
(defn start-web-server [plc-state-atom port]
  (println "Web server would start on port" port)
  ;; Return a web server instance that would:
  ;; - Serve a web UI for viewing the PLC state
  ;; - Provide API endpoints for controlling the PLC
  ;; - WebSocket for real-time updates
  {})

(defn stop-web-server [server]
  (println "Web server would stop")
  nil)

;; ---- Main Application Entry Point ----

(defn run-headless [options]
  (println "Starting ClojureLadder in headless mode...")
  
  ;; Initialize PLC state
  (let [plc-state (plc/init-plc-state)
        
        ;; Load program file if specified
        plc-state (if-let [file (:file options)]
                    (let [result (io/load-program file)]
                      (if (:success result)
                        (do
                          (println "Loaded program from" file)
                          (assoc plc-state :program (:program result)))
                        (do
                          (println "Failed to load program:" (:message result))
                          plc-state)))
                    plc-state)
        
        ;; Set run mode if specified
        plc-state (if (:run options)
                    (assoc plc-state :run-mode :run)
                    plc-state)
        
        ;; Start PLC runtime
        plc-state-atom (plc/start-plc-runtime plc-state)
        
        ;; Start Modbus server
        modbus-server (plc/init-modbus-server @plc-state-atom (:modbus-port options))
        
        ;; Start web server
        web-server (start-web-server plc-state-atom (:port options))]
    
    ;; Add shutdown hook
    (.addShutdownHook (Runtime/getRuntime)
                     (Thread. (fn []
                               (println "Shutting down...")
                               (plc/stop-plc-runtime)
                               (plc/stop-modbus-server modbus-server)
                               (stop-web-server web-server))))
    
    ;; Keep application running
    (println "ClojureLadder running. Press Ctrl-C to exit.")
    (while true
      (Thread/sleep 1000))))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      
      ;; No exit message, so proceed with program execution
      (if (or (:file options) (:run options) (:modbus-port options))
        ;; Run in headless mode with PLC runtime
        (run-headless options)
        ;; Run in GUI mode
        (simulator/run-application)))))