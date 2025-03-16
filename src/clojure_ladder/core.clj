;; ClojureLadder - A Ladder Logic Simulator in Clojure
;; Core namespace with specs, data structures, and evaluation logic

(ns clojure-ladder.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [quil.core :as q]
            [quil.middleware :as m]))

;; ---- Specs for Ladder Logic Elements ----

;; Basic element types
(s/def ::type #{:input :output :coil :contact :timer :counter})
(s/def ::id keyword?)
(s/def ::state boolean?)
(s/def ::x int?)
(s/def ::y int?)
(s/def ::width int?)
(s/def ::height int?)
(s/def ::connections (s/coll-of ::id))

;; Element-specific specs
(s/def ::normally-open boolean?)
(s/def ::preset int?)
(s/def ::current int?)
(s/def ::timeout int?)
(s/def ::negated boolean?)

;; Common element structure
(s/def ::element
  (s/keys :req-un [::id ::type ::state ::x ::y]
          :opt-un [::connections ::normally-open ::preset 
                   ::current ::timeout ::width ::height ::negated]))

;; Rung is a row in the ladder diagram
(s/def ::rung (s/coll-of ::element))

;; Program is a collection of rungs
(s/def ::program (s/coll-of ::rung))

;; Global state including I/O, timers, etc.
(s/def ::global-state (s/map-of ::id ::state))

;; ---- Data Structures ----

;; Create a new basic element
(defn create-element
  ([type id x y]
   (create-element type id x y false))
  ([type id x y state]
   {:id id
    :type type
    :state state
    :x x
    :y y
    :connections []}))

;; Create specific element types
(defn create-input [id x y state]
  (create-element :input id x y state))

(defn create-output [id x y]
  (create-element :output id x y false))

(defn create-contact [id x y normally-open]
  (merge (create-element :contact id x y false)
         {:normally-open normally-open}))

(defn create-coil [id x y]
  (create-element :coil id x y false))

(defn create-timer [id x y preset]
  (merge (create-element :timer id x y false)
         {:preset preset
          :current 0
          :timeout 0}))

(defn create-counter [id x y preset]
  (merge (create-element :counter id x y false)
         {:preset preset
          :current 0}))

;; ---- Evaluation Logic ----

;; Lookup element state
(defn get-element-state [global-state element-id]
  (get global-state element-id false))

;; Evaluate a contact
(defn evaluate-contact [global-state {:keys [id normally-open]}]
  (let [input-state (get-element-state global-state id)]
    (if normally-open input-state (not input-state))))

;; Evaluate a timer
(defn evaluate-timer [global-state delta-time {:keys [id preset current timeout] :as timer}]
  (let [input-active (get-element-state global-state id)
        new-current (if input-active (+ current delta-time) 0)
        new-timeout (if (>= new-current preset) (+ timeout delta-time) 0)
        timer-active (> new-timeout 0)]
    {:state timer-active
     :timer (assoc timer :current new-current :timeout new-timeout)}))

;; Evaluate a counter
(defn evaluate-counter [global-state prev-state {:keys [id preset current] :as counter}]
  (let [input-active (get-element-state global-state id)
        prev-input-active (get prev-state id false)
        edge-detected (and input-active (not prev-input-active))
        new-current (if edge-detected (inc current) current)
        counter-active (>= new-current preset)]
    {:state counter-active
     :counter (assoc counter :current new-current)}))

;; Evaluate a rung (series of elements in a row)
(defn evaluate-rung [global-state prev-state delta-time rung]
  (loop [elements rung
         current-state true
         updated-elements []
         updated-state global-state]
    (if (empty? elements)
      {:rung-state current-state
       :updated-elements updated-elements
       :updated-state updated-state}
      (let [element (first elements)
            element-type (:type element)]
        (case element-type
          :input (recur (rest elements) 
                        current-state 
                        (conj updated-elements element) 
                        updated-state)
          
          :contact (let [contact-state (evaluate-contact global-state element)
                         new-state (and current-state contact-state)]
                     (recur (rest elements)
                            new-state
                            (conj updated-elements element)
                            updated-state))
          
          :timer (let [{:keys [state timer]} (evaluate-timer global-state delta-time element)
                        new-state (and current-state state)]
                   (recur (rest elements)
                          new-state
                          (conj updated-elements timer)
                          updated-state))
          
          :counter (let [{:keys [state counter]} (evaluate-counter global-state prev-state element)
                          new-state (and current-state state)]
                     (recur (rest elements)
                            new-state
                            (conj updated-elements counter)
                            updated-state))
          
          :coil (let [updated-state (assoc updated-state (:id element) current-state)
                       updated-element (assoc element :state current-state)]
                  (recur (rest elements)
                         current-state
                         (conj updated-elements updated-element)
                         updated-state))
          
          :output (let [updated-element (assoc element :state current-state)]
                    (recur (rest elements)
                           current-state
                           (conj updated-elements updated-element)
                           updated-state))
          
          ;; Default case
          (recur (rest elements) 
                 current-state 
                 (conj updated-elements element) 
                 updated-state))))))

;; Evaluate an entire program
(defn evaluate-program [program global-state prev-state delta-time]
  (loop [rungs program
         current-state global-state
         updated-program []
         updated-state global-state]
    (if (empty? rungs)
      {:updated-program updated-program
       :updated-state updated-state}
      (let [rung (first rungs)
            {:keys [updated-elements updated-state]} (evaluate-rung current-state prev-state delta-time rung)]
        (recur (rest rungs)
               updated-state
               (conj updated-program updated-elements)
               updated-state)))))

;; ---- Simulation State ----

(defn init-sim-state []
  {:program []
   :global-state {}
   :prev-state {}
   :sim-time 0
   :editing false
   :selected-element nil
   :selected-tool :select
   :grid-size 40
   :auto-run true})

;; Update simulation state
(defn update-sim-state [state]
  (let [{:keys [program global-state sim-time auto-run]} state
        delta-time (if auto-run 0.1 0)  ; delta-time in seconds
        {:keys [updated-program updated-state]} (evaluate-program program global-state (:prev-state state) delta-time)]
    (assoc state 
           :program updated-program
           :global-state updated-state
           :prev-state global-state
           :sim-time (+ sim-time delta-time))))

;; ---- UI Interaction Functions ----

(defn toggle-input [state input-id]
  (let [current-state (get-in state [:global-state input-id] false)
        new-global-state (assoc (:global-state state) input-id (not current-state))]
    (assoc state :global-state new-global-state)))

(defn select-element [state x y]
  (let [{:keys [program grid-size]} state
        element-found (atom nil)]
    (doseq [rung program
            element rung]
      (let [ex (:x element)
            ey (:y element)
            width (or (:width element) grid-size)
            height (or (:height element) grid-size)]
        (when (and (>= x ex) (< x (+ ex width))
                   (>= y ey) (< y (+ ey height)))
          (reset! element-found element))))
    (assoc state :selected-element @element-found)))

(defn add-element [state element-type x y]
  (let [grid-size (:grid-size state)
        grid-x (* (int (/ x grid-size)) grid-size)
        grid-y (* (int (/ y grid-size)) grid-size)
        new-id (keyword (str (name element-type) "-" (rand-int 1000)))
        new-element (case element-type
                      :input (create-input new-id grid-x grid-y false)
                      :output (create-output new-id grid-x grid-y)
                      :contact (create-contact new-id grid-x grid-y true)
                      :coil (create-coil new-id grid-x grid-y)
                      :timer (create-timer new-id grid-x grid-y 10)
                      :counter (create-counter new-id grid-x grid-y 5)
                      nil)]
    (if new-element
      (let [rung-y (int (/ grid-y (* grid-size 2)))
            program (:program state)
            rung-exists (< rung-y (count program))
            updated-program (if rung-exists
                             (update program rung-y conj new-element)
                             (conj program [new-element]))]
        (assoc state :program updated-program))
      state)))

;; ---- Drawing Functions ----

(defn draw-grid [grid-size]
  (q/stroke 220)
  (q/stroke-weight 1)
  (let [width (q/width)
        height (q/height)]
    (doseq [x (range 0 width grid-size)]
      (q/line x 0 x height))
    (doseq [y (range 0 height grid-size)]
      (q/line 0 y width y))))

(defn draw-rail [y width]
  (q/stroke 0)
  (q/stroke-weight 2)
  (q/line 0 y width y))

(defn draw-contact [{:keys [x y state normally-open]} grid-size]
  (q/stroke 0)
  (q/stroke-weight 1)
  (let [width grid-size
        height (/ grid-size 2)]
    (q/fill 255)
    (q/rect x y width height)
    
    ;; Draw the contact symbol
    (q/line (+ x 10) (+ y (/ height 2)) (+ x width -10) (+ y (/ height 2)))
    
    ;; Draw normally open/closed symbol
    (if normally-open
      (do  ;; Draw open contact (--|  |--) 
        (q/line (+ x 20) (+ y (/ height 2) -5) (+ x width -20) (+ y (/ height 2) 5))
        (q/line (+ x 25) (+ y (/ height 2) -5) (+ x width -15) (+ y (/ height 2) 5)))
      (do  ;; Draw closed contact (--|/|--)
        (q/line (+ x 20) (+ y (/ height 2) -5) (+ x width -20) (+ y (/ height 2) 5)) 
        (q/line (+ x 25) (+ y (/ height 2) 5) (+ x width -15) (+ y (/ height 2) -5))
        (q/line (+ x 25) (+ y (/ height 2) -5) (+ x width -25) (+ y (/ height 2) 5))))
    ;; Show active state
    (when state
      (q/fill 0 255 0 100)
      (q/rect x y width height))))

(defn draw-coil [{:keys [x y state]} grid-size]
  (q/stroke 0)
  (q/stroke-weight 1)
  (let [width grid-size
        height (/ grid-size 2)]
    (q/fill 255)
    (q/rect x y width height)
    
    ;; Draw the coil circle
    (q/ellipse (+ x (/ width 2)) (+ y (/ height 2)) 20 20)
    
    ;; Show active state
    (when state
      (q/fill 0 255 0 100)
      (q/rect x y width height)
      (q/fill 0 255 0 150)
      (q/ellipse (+ x (/ width 2)) (+ y (/ height 2)) 20 20))))

(defn draw-input [{:keys [x y state id]} grid-size]
  (q/stroke 0)
  (q/stroke-weight 1)
  (let [width grid-size
        height (/ grid-size 2)]
    (q/fill 200 200 255)
    (q/rect x y width height)
    
    ;; Draw input label
    (q/fill 0)
    (q/text-align :center :center)
    (q/text (name id) (+ x (/ width 2)) (+ y (/ height 2)))
    
    ;; Show active state
    (when state
      (q/fill 0 255 0 100)
      (q/rect x y width height))))

(defn draw-output [{:keys [x y state id]} grid-size]
  (q/stroke 0)
  (q/stroke-weight 1)
  (let [width grid-size
        height (/ grid-size 2)]
    (q/fill 255 200 200)
    (q/rect x y width height)
    
    ;; Draw output label
    (q/fill 0)
    (q/text-align :center :center)
    (q/text (name id) (+ x (/ width 2)) (+ y (/ height 2)))
    
    ;; Show active state
    (when state
      (q/fill 0 255 0 100)
      (q/rect x y width height))))

(defn draw-timer [{:keys [x y state id preset current]} grid-size]
  (q/stroke 0)
  (q/stroke-weight 1)
  (let [width grid-size
        height grid-size]
    (q/fill 200 255 200)
    (q/rect x y width height)
    
    ;; Draw timer label and values
    (q/fill 0)
    (q/text-align :center :center)
    (q/text (name id) (+ x (/ width 2)) (+ y 15))
    (q/text (str "TMR " current "/" preset) (+ x (/ width 2)) (+ y (/ height 2)))
    
    ;; Show active state
    (when state
      (q/fill 0 255 0 100)
      (q/rect x y width height))))

(defn draw-counter [{:keys [x y state id preset current]} grid-size]
  (q/stroke 0)
  (q/stroke-weight 1)
  (let [width grid-size
        height grid-size]
    (q/fill 255 255 200)
    (q/rect x y width height)
    
    ;; Draw counter label and values
    (q/fill 0)
    (q/text-align :center :center)
    (q/text (name id) (+ x (/ width 2)) (+ y 15))
    (q/text (str "CNT " current "/" preset) (+ x (/ width 2)) (+ y (/ height 2)))
    
    ;; Show active state
    (when state
      (q/fill 0 255 0 100)
      (q/rect x y width height))))

(defn draw-element [element grid-size]
  (case (:type element)
    :contact (draw-contact element grid-size)
    :coil (draw-coil element grid-size)
    :input (draw-input element grid-size)
    :output (draw-output element grid-size)
    :timer (draw-timer element grid-size)
    :counter (draw-counter element grid-size)
    nil))

(defn draw-program [program grid-size]
  (doseq [[rung-idx rung] (map-indexed vector program)]
    (let [y (* rung-idx grid-size 2)
          rail-y (+ y (/ grid-size 2))]
      ;; Draw the horizontal power rail
      (draw-rail rail-y (q/width))
      
      ;; Draw each element in the rung
      (doseq [element rung]
        (draw-element element grid-size)))))

(defn draw-toolbar [selected-tool]
  (let [tools [:select :input :output :contact :coil :timer :counter]
        tool-labels {"select" "Select", "input" "Input", "output" "Output", 
                    "contact" "Contact", "coil" "Coil", "timer" "Timer", "counter" "Counter"}
        toolbar-height 30
        toolbar-width (/ (q/width) (count tools))]
    
    (q/fill 220)
    (q/no-stroke)
    (q/rect 0 0 (q/width) toolbar-height)
    
    (doseq [[idx tool] (map-indexed vector tools)]
      (let [x (* idx toolbar-width)
            selected? (= tool selected-tool)]
        ;; Highlight selected tool
        (when selected?
          (q/fill 180)
          (q/rect x 0 toolbar-width toolbar-height))
        
        ;; Draw tool name
        (q/fill 0)
        (q/text-align :center :center)
        (q/text (get tool-labels (name tool) (name tool)) 
                (+ x (/ toolbar-width 2)) 
                (/ toolbar-height 2))))))

(defn draw-state [state]
  (q/background 255)
  (let [{:keys [program grid-size selected-tool]} state]
    ;; Draw grid
    (draw-grid grid-size)
    
    ;; Draw program
    (draw-program program grid-size)
    
    ;; Draw toolbar
    (draw-toolbar selected-tool)))

;; ---- Mouse Interaction ----

(defn mouse-pressed [state event]
  (let [{:keys [x y] :or {x 0 y 0}} event
        {:keys [selected-tool]} state]
    (cond
      ;; Toolbar clicks
      (< y 30) (let [toolbar-width (/ (q/width) 7)
                     tool-idx (int (/ x toolbar-width))
                     tools [:select :input :output :contact :coil :timer :counter]
                     new-tool (nth tools tool-idx nil)]
                 (if new-tool
                   (assoc state :selected-tool new-tool)
                   state))
      
      ;; Canvas clicks
      :else (case selected-tool
              :select (select-element state x y)
              (add-element state selected-tool x y)))))

;; ---- Example Programs ----

(defn simple-program []
  (let [input1 (create-input :input1 40 40 false)
        contact1 (create-contact :input1 120 40 true)
        output1 (create-output :output1 200 40)
        input2 (create-input :input2 40 120 false)
        contact2 (create-contact :input2 120 120 true)
        timer1 (create-timer :timer1 200 120 5)]
    [[input1 contact1 contact2 output1]
     [input2 contact2 timer1]]))

;; ---- Main Sketch ----

(defn setup []
  (q/frame-rate 30)
  (q/color-mode :rgb)
  (q/text-font (q/create-font "Arial" 12))
  (let [initial-state (init-sim-state)
        program (simple-program)]
    (assoc initial-state :program program)))

(defn update-state [state]
  (update-sim-state state))

(defn -main []
  (q/defsketch clojure-ladder
    :title "ClojureLadder - Ladder Logic Simulator"
    :size [800 600]
    :setup setup
    :update update-state
    :draw draw-state
    :mouse-pressed mouse-pressed
    :features [:keep-on-top]
    :middleware [m/fun-mode]))
