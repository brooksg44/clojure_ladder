(ns clojure-ladder.simulator
  (:require [clojure.spec.alpha :as s]
            [quil.core :as q]
            [quil.middleware :as m]
            [clojure.core.async :as async]
            [clojure-ladder.core :as core]
            [clojure-ladder.io :as io]
            [clojure.string :as str]))

(def sim-state (atom (core/init-sim-state)))

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

;; ---- Drawing UI Components ----

(defn draw-menu-background []
  (let [width (q/width)
        height (q/height)]
    ;; Semi-transparent background
    (q/fill 0 0 0 150)
    (q/rect 0 0 width height)

    ;; Menu panel
    (q/fill 240)
    (q/stroke 180)
    (q/stroke-weight 2)
    (q/rect (/ width 4) (/ height 4) (/ width 2) (/ height 2) 10)))

(defn draw-button [x y width height text active?]
  (q/stroke 180)
  (q/stroke-weight 1)
  (if active?
    (q/fill 200 220 255)
    (q/fill 220))
  (q/rect x y width height 5)

  (q/fill 0)
  (q/text-align :center :center)
  (q/text text (+ x (/ width 2)) (+ y (/ height 2))))

(defn draw-input-field [x y width height text]
  (q/stroke 180)
  (q/stroke-weight 1)
  (q/fill 255)
  (q/rect x y width height 5)

  (q/fill 0)
  (q/text-align :left :center)
  (q/text text (+ x 10) (+ y (/ height 2))))

(defn draw-main-menu []
  (let [width (q/width)
        height (q/height)
        panel-x (/ width 4)
        panel-y (/ height 4)
        panel-width (/ width 2)
        panel-height (/ height 2)
        button-width 200
        button-height 40
        button-x (+ panel-x (/ panel-width 2) (/ button-width -2))
        button-spacing 50]

    (draw-menu-background)

    ;; Title
    (q/fill 0)
    (q/text-size 24)
    (q/text-align :center :center)
    (q/text "ClojureLadder" (+ panel-x (/ panel-width 2)) (+ panel-y 30))
    (q/text-size 12)

    ;; Menu buttons
    (draw-button button-x (+ panel-y 80) button-width button-height "New Program" false)
    (draw-button button-x (+ panel-y 80 button-spacing) button-width button-height "Load Program" false)
    (draw-button button-x (+ panel-y 80 (* button-spacing 2)) button-width button-height "Save Program" false)
    (draw-button button-x (+ panel-y 80 (* button-spacing 3)) button-width button-height "Export Program" false)
    (draw-button button-x (+ panel-y 80 (* button-spacing 4)) button-width button-height "Close Menu" false)))

(defn draw-file-dialog [dialog-type]
  (let [width (q/width)
        height (q/height)
        panel-x (/ width 4)
        panel-y (/ height 4)
        panel-width (/ width 2)
        panel-height (/ height 2)
        input-width 300
        button-width 100
        button-height 40]

    (draw-menu-background)

    ;; Title
    (q/fill 0)
    (q/text-size 18)
    (q/text-align :center :center)
    (q/text (case dialog-type
              :save "Save Program"
              :load "Load Program"
              :export "Export Program"
              "File Dialog")
            (+ panel-x (/ panel-width 2)) (+ panel-y 30))
    (q/text-size 12)

    ;; Filename input
    (q/text "Filename:" (+ panel-x 80) (+ panel-y 100))
    (draw-input-field (+ panel-x 80) (+ panel-y 120) input-width 30 (:filename @menu-state))

    ;; Buttons
    (draw-button (+ panel-x 80) (+ panel-y 170) button-width button-height "OK" false)
    (draw-button (+ panel-x 80 button-width 20) (+ panel-y 170) button-width button-height "Cancel" false)))

(defn draw-message []
  (when-let [message (:message @menu-state)]
    (let [width (q/width)
          height (q/height)
          msg-width (+ (q/text-width message) 40)
          msg-height 40
          msg-x (- (/ width 2) (/ msg-width 2))
          msg-y 50]

      ;; Draw message box
      (q/fill 250 250 220)
      (q/stroke 200 200 150)
      (q/stroke-weight 2)
      (q/rect msg-x msg-y msg-width msg-height 10)

      ;; Draw message text
      (q/fill 0)
      (q/text-align :center :center)
      (q/text message (/ width 2) (+ msg-y (/ msg-height 2))))))

(defn draw-control-panel [state]
  (let [height 40
        button-width 100
        button-height 30
        panel-x 10
        panel-y (- (q/height) height 10)]

    ;; Panel background
    (q/fill 240)
    (q/stroke 200)
    (q/stroke-weight 1)
    (q/rect panel-x panel-y (- (q/width) 20) height 5)

    ;; Control buttons
    (draw-button (+ panel-x 10) (+ panel-y 5) button-width button-height "Menu" false)
    (draw-button (+ panel-x 10 (* 1 (+ button-width 10))) (+ panel-y 5) button-width button-height "Run" false)
    (draw-button (+ panel-x 10 (* 2 (+ button-width 10))) (+ panel-y 5) button-width button-height "Stop" false)
    (draw-button (+ panel-x 10 (* 3 (+ button-width 10))) (+ panel-y 5) button-width button-height "Step" false)

    ;; Simulation info
    (q/fill 0)
    (q/text-align :right :center)
    (q/text (str "Simulation time: "
                 (if-let [t (:sim-time state)]
                   (format "%.1f" (float t))
                   "0.0")
                 "s")
            (- (q/width) 20) (+ panel-y (/ height 2)))))

(defn draw-menus []
  (when (:active @menu-state)
    (case (:current-menu @menu-state)
      :main (draw-main-menu)
      :file-dialog (draw-file-dialog (:file-dialog-type @menu-state))
      (draw-main-menu))))

;; ---- Mouse Interaction with UI ----

(defn check-button-click [x y button-x button-y button-width button-height]
  (and (>= x button-x) (< x (+ button-x button-width))
       (>= y button-y) (< y (+ button-y button-height))))

(defn handle-main-menu-click [state x y]
  (let [width (q/width)
        height (q/height)
        panel-x (/ width 4)
        panel-y (/ height 4)
        panel-width (/ width 2)
        panel-height (/ height 2)
        button-width 200
        button-height 40
        button-x (+ panel-x (/ panel-width 2) (/ button-width -2))
        button-spacing 50]

    (cond
      ;; New Program button
      (check-button-click x y button-x (+ panel-y 80) button-width button-height)
      (do
        (hide-menu)
        (assoc state :program [] :global-state {}))

      ;; Load Program button
      (check-button-click x y button-x (+ panel-y 80 button-spacing) button-width button-height)
      (do
        (swap! menu-state assoc :current-menu :file-dialog :file-dialog-type :load)
        state)

      ;; Save Program button
      (check-button-click x y button-x (+ panel-y 80 (* button-spacing 2)) button-width button-height)
      (do
        (swap! menu-state assoc :current-menu :file-dialog :file-dialog-type :save)
        state)

      ;; Export Program button
      (check-button-click x y button-x (+ panel-y 80 (* button-spacing 3)) button-width button-height)
      (do
        (swap! menu-state assoc :current-menu :file-dialog :file-dialog-type :export)
        state)

      ;; Close Menu button
      (check-button-click x y button-x (+ panel-y 80 (* button-spacing 4)) button-width button-height)
      (do
        (hide-menu)
        state)

      :else state)))

(defn handle-file-dialog-click [state x y]
  (let [width (q/width)
        height (q/height)
        panel-x (/ width 4)
        panel-y (/ height 4)
        panel-width (/ width 2)
        panel-height (/ height 2)
        button-width 100
        button-height 40]

    (cond
      ;; OK button
      (check-button-click x y (+ panel-x 80) (+ panel-y 170) button-width button-height)
      (let [filename (:filename @menu-state)
            dialog-type (:file-dialog-type @menu-state)]
        (case dialog-type
          :save (do
                  (save-current-program state filename)
                  (hide-menu)
                  state)
          :load (do
                  (let [updated-state (load-program-file state filename)]
                    (hide-menu)
                    updated-state))
          :export (do
                    (let [export-text (io/export-to-text (:program state))
                          _ (spit filename export-text)]
                      (show-message (str "Program exported to " filename))
                      (hide-menu)
                      state))
          (do
            (hide-menu)
            state)))

      ;; Cancel button
      (check-button-click x y (+ panel-x 80 button-width 20) (+ panel-y 170) button-width button-height)
      (do
        (hide-menu)
        state)

      :else state)))

(defn handle-control-panel-click [state x y]
  (let [height 40
        button-width 100
        button-height 30
        panel-x 10
        panel-y (- (q/height) height 10)]

    (cond
      ;; Menu button
      (check-button-click x y (+ panel-x 10) (+ panel-y 5) button-width button-height)
      (do
        (show-menu :main)
        state)

      ;; Run button
      (check-button-click x y (+ panel-x 10 (* 1 (+ button-width 10))) (+ panel-y 5) button-width button-height)
      (start-simulation state)

      ;; Stop button
      (check-button-click x y (+ panel-x 10 (* 2 (+ button-width 10))) (+ panel-y 5) button-width button-height)
      (stop-simulation state)

      ;; Step button
      (check-button-click x y (+ panel-x 10 (* 3 (+ button-width 10))) (+ panel-y 5) button-width button-height)
      (step-simulation state)

      :else state)))

(defn handle-ui-mouse-click [state x y]
  (cond
    ;; Handle menu clicks if menu is active
    (:active @menu-state)
    (case (:current-menu @menu-state)
      :main (handle-main-menu-click state x y)
      :file-dialog (handle-file-dialog-click state x y)
      state)

    ;; Handle toolbar clicks (top 30px)
    (< y 30)
    (core/mouse-pressed state {:x x :y y})

    ;; Handle control panel clicks (bottom of screen)
    (> y (- (q/height) 50))
    (handle-control-panel-click state x y)

    ;; Handle canvas clicks
    :else
    (core/mouse-pressed state {:x x :y y})))

;; ---- Keyboard Handling ----

(defn key-pressed [state event]
  (let [key-code (:key-code event)
        key-char (:key event)]

    (cond
      ;; If file dialog is active, update filename
      (and (:active @menu-state)
           (= :file-dialog (:current-menu @menu-state)))
      (case key-code
        ;; Backspace
        8 (do
            (when (> (count (:filename @menu-state)) 0)
              (swap! menu-state update :filename #(subs % 0 (dec (count %)))))
            state)
        ;; Enter
        13 (handle-file-dialog-click state
                                     (+ (/ (q/width) 4) 80)
                                     (+ (/ (q/height) 4) 170))
        ;; Escape
        27 (do
             (hide-menu)
             state)
        ;; Regular character
        (do
          (when (and (not= key-char :key-coded) (< (count (:filename @menu-state)) 30))
            (swap! menu-state update :filename #(str % key-char)))
          state))

      ;; Escape key to show menu
      (= key-code 27)
      (do
        (if (:active @menu-state)
          (hide-menu)
          (show-menu :main))
        state)

      ;; Space bar to pause/resume simulation
      (= key-code 32)
      (if (:auto-run state)
        (stop-simulation state)
        (start-simulation state))

      ;; S key to step simulation
      (= (str/lower-case (str key-char)) "s")
      (step-simulation state)

      ;; Default - no action
      :else state)))

;; ---- Update and Draw ----

(defn update-ui-state [state]
  (update-message-timer)
  (if (:active @menu-state)
    state  ; Don't update simulation if menu is active
    (core/update-sim-state #p state)))

(defn draw-ui [state]
  (q/background 255)
  (core/draw-state state)
  (draw-control-panel state)
  (draw-menus)
  (draw-message))

;; ---- Main Application ----



(defn setup []
  (q/frame-rate 60)
  (q/color-mode :rgb)
  (q/text-font (q/create-font "Arial" 12))
  (reset! sim-state (core/init-sim-state))
  (swap! sim-state assoc :program (core/simple-program))
  @sim-state)

(defn mouse-clicked [state event]
  (handle-ui-mouse-click state (:x event) (:y event)))

(defn run-application []
  (q/defsketch clojure-ladder
    :title "ClojureLadder - Ladder Logic Simulator"
    :size [1024 768]
    :setup setup
    :update update-ui-state
    :draw draw-ui
    :mouse-clicked mouse-clicked
    :key-pressed key-pressed
    :features [:keep-on-top]
    :middleware [m/fun-mode]))

(defn -main []
  (run-application))
