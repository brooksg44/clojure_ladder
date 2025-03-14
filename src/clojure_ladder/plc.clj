(ns clojure-ladder.plc
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [clojure-ladder.core :as core]))

;; ---- PLC Configuration ----

(s/def ::scan-rate number?)  ; Scan rate in milliseconds
(s/def ::run-mode #{:run :stop :single-step})
(s/def ::digital-inputs (s/map-of keyword? boolean?))
(s/def ::digital-outputs (s/map-of keyword? boolean?))
(s/def ::analog-inputs (s/map-of keyword? number?))
(s/def ::analog-outputs (s/map-of keyword? number?))
(s/def ::memory-bits (s/map-of keyword? boolean?))
(s/def ::memory-words (s/map-of keyword? number?))

(s/def ::plc-state
  (s/keys :req-un [::scan-rate ::run-mode ::program
                   ::digital-inputs ::digital-outputs
                   ::analog-inputs ::analog-outputs
                   ::memory-bits ::memory-words]))

;; Initialize a new PLC state
(defn init-plc-state []
  {:scan-rate 100  ; 100ms scan rate (10 scans per second)
   :run-mode :stop
   :program []
   :digital-inputs {}
   :digital-outputs {}
   :analog-inputs {}
   :analog-outputs {}
   :memory-bits {}
   :memory-words {}})

;; ---- IO Operations ----

(defn set-digital-input [plc-state input-id value]
  (assoc-in plc-state [:digital-inputs input-id] value))

(defn get-digital-output [plc-state output-id]
  (get-in plc-state [:digital-outputs output-id] false))

(defn set-analog-input [plc-state input-id value]
  (assoc-in plc-state [:analog-inputs input-id] value))

(defn get-analog-output [plc-state output-id]
  (get-in plc-state [:analog-outputs output-id] 0))

(defn read-memory-bit [plc-state bit-id]
  (get-in plc-state [:memory-bits bit-id] false))

(defn write-memory-bit [plc-state bit-id value]
  (assoc-in plc-state [:memory-bits bit-id] value))

(defn read-memory-word [plc-state word-id]
  (get-in plc-state [:memory-words word-id] 0))

(defn write-memory-word [plc-state word-id value]
  (assoc-in plc-state [:memory-words word-id] value))

;; ---- PLC Program Mapping ----

;; Map virtual I/O to PLC I/O
(defn map-io-to-global-state [plc-state]
  (let [digital-inputs (:digital-inputs plc-state)
        memory-bits (:memory-bits plc-state)]
    (merge {} digital-inputs memory-bits)))

;; Update PLC state from program outputs
(defn update-plc-from-global-state [plc-state global-state]
  (let [updated-outputs (reduce-kv
                         (fn [outputs k v]
                           (if (= (name k) (clojure.string/replace (name k) #"^output" ""))
                             outputs  ; Not an output
                             (assoc outputs k v)))  ; Is an output
                         {}
                         global-state)
        updated-memory (reduce-kv
                        (fn [memory k v]
                          (if (.startsWith (name k) "memory")
                            (assoc memory k v)
                            memory))
                        {}
                        global-state)]
    (-> plc-state
        (update :digital-outputs merge updated-outputs)
        (update :memory-bits merge updated-memory))))

;; ---- PLC Execution ----

;; Execute one scan of the PLC
(defn execute-scan [plc-state]
  (if (= (:run-mode plc-state) :stop)
    plc-state
    (let [program (:program plc-state)
          global-state (map-io-to-global-state plc-state)
          {:keys [updated-program updated-state]}
          (core/evaluate-program program global-state {} 0.1)  ; 0.1s delta for timers
          updated-plc (update-plc-from-global-state plc-state updated-state)]
      (-> updated-plc
          (assoc :program updated-program)
          (assoc :run-mode (if (= (:run-mode plc-state) :single-step) :stop :run))))))

;; ---- PLC Runtime ----

(defonce plc-channel (async/chan))
(defonce plc-control-channel (async/chan))
(defonce plc-running (atom false))

;; Start the PLC runtime
(defn start-plc-runtime [initial-state]
  (let [plc-state (atom initial-state)]
    (reset! plc-running true)
    (async/go-loop []
      (when @plc-running
        (let [scan-rate (:scan-rate @plc-state)
              timeout-ch (async/timeout scan-rate)]

          ;; Execute one scan
          (when (not= (:run-mode @plc-state) :stop)
            (swap! plc-state execute-scan))

          ;; Check for control messages
          (async/alt!
            plc-control-channel ([cmd]
                                 (case cmd
                                   :stop (reset! plc-running false)
                                   :reset (reset! plc-state (init-plc-state))
                                   nil))

            plc-channel ([msg]
                         (when msg
                           (swap! plc-state #(merge % msg))))

            timeout-ch ([_] nil)

            :priority true)

          (recur))))
    plc-state))

;; Stop the PLC runtime
(defn stop-plc-runtime []
  (async/>!! plc-control-channel :stop)
  (reset! plc-running false))

;; Update the PLC state
(defn update-plc! [update-map]
  (async/>!! plc-channel update-map))

;; Set the PLC run mode
(defn set-run-mode! [mode]
  (when (#{:run :stop :single-step} mode)
    (update-plc! {:run-mode mode})))

;; ---- Modbus Interface ----

;; This would be a full implementation for Modbus TCP/IP and RTU
;; but for brevity just providing stubs for the key functionality

(defn init-modbus-server [plc-state port]
  ;; Initialize a Modbus TCP server on the specified port
  ;; Map PLC I/O to Modbus registers
  ;; Return a server instance
  (println "Modbus server would start on port" port)
  {})

(defn stop-modbus-server [server]
  ;; Stop the Modbus server
  (println "Modbus server would stop")
  nil)

(defn init-modbus-client [host port]
  ;; Initialize a Modbus client to connect to other devices
  ;; Return a client instance
  (println "Modbus client would connect to" host "on port" port)
  {})

(defn read-modbus-register [client address]
  ;; Read a value from a Modbus register
  ;; Return the value
  0)

(defn write-modbus-register [client address value]
  ;; Write a value to a Modbus register
  ;; Return success/failure
  true)

;; ---- IEC 61131-3 Function Blocks ----

;; RS (Reset-Set) Flip-Flop
(defn fb-rs [set reset prev-state]
  (let [q (if reset false (or prev-state set))]
    {:q q :q1 (not q)}))

;; SR (Set-Reset) Flip-Flop
(defn fb-sr [set reset prev-state]
  (let [q (if set true (and prev-state (not reset)))]
    {:q q :q1 (not q)}))

;; TON (Timer On-Delay)
(defn fb-ton [in preset-time elapsed-time delta-time]
  (let [new-elapsed (if in (+ elapsed-time delta-time) 0)
        q (>= new-elapsed preset-time)]
    {:q q :et new-elapsed}))

;; TOF (Timer Off-Delay)
(defn fb-tof [in preset-time elapsed-time delta-time]
  (let [timing (and (not in) (> elapsed-time 0))
        new-elapsed (cond
                      in preset-time
                      timing (max 0 (- elapsed-time delta-time))
                      :else 0)
        q (> new-elapsed 0)]
    {:q q :et new-elapsed}))

;; TP (Pulse Timer)
(defn fb-tp [in preset-time elapsed-time delta-time]
  (let [rising-edge (and in (= elapsed-time 0))
        timing (< elapsed-time preset-time)
        new-elapsed (cond
                      rising-edge delta-time
                      timing (+ elapsed-time delta-time)
                      :else 0)
        q (> new-elapsed 0)]
    {:q q :et new-elapsed}))

;; CTU (Counter Up)
(defn fb-ctu [cu r pv cv]
  (let [prev-cu (get cv :prev-cu false)
        rising-edge (and cu (not prev-cu))
        new-cv (cond
                 r 0
                 rising-edge (inc (:count cv 0))
                 :else (:count cv 0))
        q (>= new-cv pv)]
    {:q q :cv new-cv :prev-cu cu}))

;; CTD (Counter Down)
(defn fb-ctd [cd ld pv cv]
  (let [prev-cd (get cv :prev-cd false)
        rising-edge (and cd (not prev-cd))
        new-cv (cond
                 ld pv
                 rising-edge (dec (:count cv 0))
                 :else (:count cv 0))
        q (<= new-cv 0)]
    {:q q :cv new-cv :prev-cd cd}))

;; CTUD (Counter Up-Down)
(defn fb-ctud [cu cd r ld pv cv]
  (let [prev-cu (get cv :prev-cu false)
        prev-cd (get cv :prev-cd false)
        rising-edge-cu (and cu (not prev-cu))
        rising-edge-cd (and cd (not prev-cd))
        new-cv (cond
                 r 0
                 ld pv
                 rising-edge-cu (inc (:count cv 0))
                 rising-edge-cd (dec (:count cv 0))
                 :else (:count cv 0))
        qu (>= new-cv pv)
        qd (<= new-cv 0)]
    {:qu qu :qd qd :cv new-cv :prev-cu cu :prev-cd cd}))
