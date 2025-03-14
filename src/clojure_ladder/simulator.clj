(ns clojure-ladder.simulator
  (:require [clojure.spec.alpha :as s]
            [quil.core :as q]
            [quil.middleware :as m]
            [clojure.core.async :as async]
            [clojure-ladder.core :as core]
            [clojure-ladder.io :as io]))

;; ---- Menu and UI State ----

(def menu-state (atom {:active false
                       :current-menu :main
                       :file-dialog false
                       :filename "program.edn"
                       :message nil
                       :message-timer 0}))

(defn show-menu [menu-type]
  (swap! menu-state assoc :active true :current-menu menu-type))

(defn hide-menu []
  (swap! menu-state assoc :active false))

(defn show-message [message]
  (swap! menu-state assoc :message message :message-timer 180))  ; Show for 3 seconds (60 fps * 3)

(defn update-message-timer []
  (when (> (:message-timer @menu-state) 0)
    (swap! menu-state update :message-timer dec)
    (when (zero? (:message-timer @menu-state))
      (swap! menu-state assoc :message nil))))

;; ---- File Operations ----

(defn save-current-program [state filename]
  (let [result (io/save-program filename (:program state))]
    (show-message (:message result))
    state))

(defn load-program-file [state filename]
  (let [result (io/load-program filename)]
    (if (:success result)
      (do
        (show-message (str "Program loaded from " filename))
        (assoc state :program (:program result)))
      (do
        (show-message (:message result))
        state))))

;; ---- Simulation Control ----

(defn start-simulation [state]
  (assoc state :auto-run true))

(defn stop-simulation [state]
  (assoc state :auto-run false))

(defn step-simulation [state]
  (let [updated-state (core/update-sim-state state)]
    (assoc updated-state :auto-run false)))