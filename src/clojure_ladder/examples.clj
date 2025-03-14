(ns clojure-ladder.examples
  (:require [clojure-ladder.core :as core]))

;; ---- Example Programs ----

(defn motor-start-stop
  "Simple motor start/stop circuit with latching logic"
  []
  (let [start-button (core/create-input :start 40 40 false)
        stop-button (core/create-input :stop 40 120 false)
        stop-contact (core/create-contact :stop 120 120 false) ; Normally closed contact
        motor-contact (core/create-contact :motor 200 40 true)
        start-contact (core/create-contact :start 120 40 true)
        motor-coil (core/create-coil :motor 280 40)]
    [[start-button start-contact motor-contact motor-coil]
     [stop-button stop-contact]]))

(defn timer-example
  "Timer example with on-delay timer"
  []
  (let [start-button (core/create-input :start 40 40 false)
        start-contact (core/create-contact :start 120 40 true)
        timer (core/create-timer :timer1 200 40 30) ; 3 second timer
        lamp-coil (core/create-coil :lamp 280 40)
        timer-contact (core/create-contact :timer1 120 120 true)
        buzzer-coil (core/create-coil :buzzer 200 120)]
    [[start-button start-contact timer]
     [timer-contact buzzer-coil]
     [start-contact lamp-coil]]))

(defn counter-example
  "Counter example with reset"
  []
  (let [count-button (core/create-input :count 40 40 false)
        reset-button (core/create-input :reset 40 120 false)
        count-contact (core/create-contact :count 120 40 true)
        counter (core/create-counter :counter1 200 40 5) ; Count to 5
        reset-contact (core/create-contact :reset 120 120 true)
        counter-reset (core/create-coil :counter_reset 200 120)
        counter-done-contact (core/create-contact :counter1 120 200 true)
        done-coil (core/create-coil :done 200 200)]
    [[count-button count-contact counter]
     [reset-button reset-contact counter-reset]
     [counter-done-contact done-coil]]))

(defn traffic-light
  "Traffic light control sequence example"
  []
  (let [; Inputs
        sensor (core/create-input :sensor 40 40 false)
        ; Timers
        timer-green (core/create-timer :timer_green 200 40 50) ; 5 seconds
        timer-yellow (core/create-timer :timer_yellow 200 100 20) ; 2 seconds
        timer-red (core/create-timer :timer_red 200 160 70) ; 7 seconds
        ; Outputs
        green-light (core/create-coil :green 280 40)
        yellow-light (core/create-coil :yellow 280 100)
        red-light (core/create-coil :red 280 160)
        ; Contacts
        sensor-contact (core/create-contact :sensor 120 40 true)
        red-contact (core/create-contact :red 120 100 true)
        green-timer-contact (core/create-contact :timer_green 120 160 true)
        yellow-timer-contact (core/create-contact :timer_yellow 120 220 true)]
    [[sensor-contact timer-green green-light]
     [red-contact timer-green]
     [green-timer-contact timer-yellow yellow-light]
     [yellow-timer-contact timer-red red-light]]))

(defn conveyor-system
  "Conveyor belt control system with multiple sensors"
  []
  (let [; Inputs
        start-button (core/create-input :start 40 40 false)
        stop-button (core/create-input :stop 40 100 false)
        emergency-stop (core/create-input :emergency 40 160 false)
        sensor-1 (core/create-input :sensor1 40 220 false)
        sensor-2 (core/create-input :sensor2 40 280 false)
        
        ; Contacts
        start-contact (core/create-contact :start 120 40 true)
        conveyor-contact (core/create-contact :conveyor 200 40 true)
        stop-contact (core/create-contact :stop 120 100 false) ; Normally closed
        emergency-contact (core/create-contact :emergency 200 100 false) ; Normally closed
        sensor-1-contact (core/create-contact :sensor1 120 160 true)
        sensor-2-contact (core/create-contact :sensor2 120 220 true)
        
        ; Outputs
        conveyor-motor (core/create-coil :conveyor 280 40)
        alarm (core/create-coil :alarm 280 160)
        light (core/create-coil :light 280 220)]
    [[start-contact conveyor-contact conveyor-motor]
     [stop-contact emergency-contact]
     [sensor-1-contact alarm]
     [sensor-2-contact light]]))

(defn sequential-process
  "Sequential process control example"
  []
  (let [; Inputs
        start-button (core/create-input :start 40 40 false)
        reset-button (core/create-input :reset 40 340 false)
        
        ; Step flags (coils)
        step1-coil (core/create-coil :step1 280 40)
        step2-coil (core/create-coil :step2 280 100)
        step3-coil (core/create-coil :step3 280 160)
        step4-coil (core/create-coil :step4 280 220)
        done-coil (core/create-coil :done 280 280)
        reset-coil (core/create-coil :reset_seq 280 340)
        
        ; Step contacts
        start-contact (core/create-contact :start 120 40 true)
        done-contact (core/create-contact :done 200 40 false) ; Normally closed
        step1-contact-1 (core/create-contact :step1 120 100 true)
        timer1-contact (core/create-contact :timer1 200 100 true)
        step2-contact-1 (core/create-contact :step2 120 160 true)
        timer2-contact (core/create-contact :timer2 200 160 true)
        step3-contact-1 (core/create-contact :step3 120 220 true)
        timer3-contact (core/create-contact :timer3 200 220 true)
        step4-contact-1 (core/create-contact :step4 120 280 true)
        reset-contact (core/create-contact :reset 120 340 true)
        
        ; Timers
        timer1 (core/create-timer :timer1 360 100 30) ; 3 seconds
        timer2 (core/create-timer :timer2 360 160 40) ; 4 seconds
        timer3 (core/create-timer :timer3 360 220 20)] ; 2 seconds
    
    [[start-contact done-contact step1-coil]
     [step1-contact-1 timer1 timer1-contact step2-coil]
     [step2-contact-1 timer2 timer2-contact step3-coil]
     [step3-contact-1 timer3 timer3-contact step4-coil]
     [step4-contact-1 done-coil]
     [reset-contact reset-coil]]))

(defn get-example-program
  "Returns the specified example program"
  [example-name]
  (case example-name
    :motor-start-stop (motor-start-stop)
    :timer-example (timer-example)
    :counter-example (counter-example)
    :traffic-light (traffic-light)
    :conveyor-system (conveyor-system)
    :sequential-process (sequential-process)
    ;; Default to simple program if example not found
    (core/simple-program)))

(defn list-examples
  "Returns a list of available example programs"
  []
  [:motor-start-stop
   :timer-example
   :counter-example
   :traffic-light
   :conveyor-system
   :sequential-process])