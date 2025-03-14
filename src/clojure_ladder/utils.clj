(ns clojure-ladder.utils
  (:require [clojure.spec.alpha :as s]
            [clojure.data.json :as json]))

;; ---- Validation Utilities ----

(defn validate-element
  "Validates a ladder logic element against specs"
  [element]
  (if (s/valid? :clojure-ladder.core/element element)
    {:valid true :element element}
    {:valid false :problems (s/explain-data :clojure-ladder.core/element element)}))

(defn validate-program
  "Validates an entire ladder program against specs"
  [program]
  (if (s/valid? :clojure-ladder.core/program program)
    {:valid true :program program}
    {:valid false :problems (s/explain-data :clojure-ladder.core/program program)}))

;; ---- Conversion Utilities ----

(defn program->json
  "Converts a ladder program to JSON string"
  [program]
  (json/write-str program))

(defn json->program
  "Converts a JSON string to a ladder program"
  [json-str]
  (try
    (let [program (json/read-str json-str :key-fn keyword)]
      (if (s/valid? :clojure-ladder.core/program program)
        {:valid true :program program}
        {:valid false :message "Invalid program structure"}))
    (catch Exception e
      {:valid false :message (str "JSON parsing error: " (.getMessage e))})))

;; ---- Program Analysis ----

(defn find-element-by-id
  "Finds an element by its ID in the program"
  [program element-id]
  (first
   (for [rung program
         element rung
         :when (= (:id element) element-id)]
     element)))

(defn find-elements-by-type
  "Finds all elements of a given type in the program"
  [program element-type]
  (vec
   (for [rung program
         element rung
         :when (= (:type element) element-type)]
     element)))

(defn find-inputs
  "Finds all input elements in the program"
  [program]
  (find-elements-by-type program :input))

(defn find-outputs
  "Finds all output elements in the program"
  [program]
  (find-elements-by-type program :output))

(defn count-element-types
  "Counts the occurrences of each element type in the program"
  [program]
  (reduce
   (fn [counts element]
     (update counts (:type element) (fnil inc 0)))
   {}
   (flatten program)))

;; ---- Program Transformation ----

(defn add-element-to-rung
  "Adds an element to a specific rung in the program"
  [program rung-idx element]
  (update program rung-idx conj element))

(defn update-element-by-id
  "Updates an element by ID with the given updater function"
  [program element-id updater-fn]
  (mapv
   (fn [rung]
     (mapv
      (fn [element]
        (if (= (:id element) element-id)
          (updater-fn element)
          element))
      rung))
   program))

(defn remove-element-by-id
  "Removes an element by its ID from the program"
  [program element-id]
  (mapv
   (fn [rung]
     (into [] (remove #(= (:id %) element-id) rung)))
   program))

(defn add-new-rung
  "Adds a new empty rung to the program"
  [program]
  (conj program []))

(defn remove-rung
  "Removes a rung at the given index"
  [program rung-idx]
  (if (< rung-idx (count program))
    (vec (concat (subvec program 0 rung-idx) (subvec program (inc rung-idx))))
    program))

;; ---- Element Creation Helpers ----

(defn create-unique-id
  "Creates a unique element ID based on type and existing elements"
  [program element-type]
  (let [type-name (name element-type)
        existing-ids (map :id (flatten program))
        last-num (->> existing-ids
                     (filter #(.startsWith (name %) type-name))
                     (map #(subs (name %) (count type-name)))
                     (filter #(re-matches #"\d+" %))
                     (map #(Integer/parseInt %))
                     (reduce max 0))]
    (keyword (str type-name (inc last-num)))))

;; ---- Grid/Layout Utilities ----

(defn nearest-grid-point
  "Finds the nearest grid point for x,y coordinates"
  [x y grid-size]
  [(* (Math/round (/ x grid-size)) grid-size)
   (* (Math/round (/ y grid-size)) grid-size)])

(defn find-element-at-position
  "Finds an element at the given position"
  [program x y]
  (first
   (for [rung program
         element rung
         :let [ex (:x element)
               ey (:y element)
               width (or (:width element) 40)
               height (or (:height element) 40)]
         :when (and (>= x ex) (< x (+ ex width))
                   (>= y ey) (< y (+ ey height)))]
     element)))

;; ---- Simulation Utilities ----

(defn rung-has-output?
  "Checks if a rung contains an output element"
  [rung]
  (some #(= (:type %) :output) rung))

(defn rung-has-coil?
  "Checks if a rung contains a coil element"
  [rung]
  (some #(= (:type %) :coil) rung))

(defn validate-rung-structure
  "Validates that a rung has a proper structure with inputs and outputs"
  [rung]
  (and (not-empty rung)
       (or (rung-has-output? rung)
           (rung-has-coil? rung))))

(defn compute-program-execution-order
  "Computes the execution order of rungs in a program"
  [program]
  (let [coil-ids (set (map :id (find-elements-by-type program :coil)))
        contact-ids-by-rung (mapv
                             (fn [rung]
                               (set (map :id (filter #(= (:type %) :contact) rung))))
                             program)
        coil-ids-by-rung (mapv
                          (fn [rung]
                            (set (map :id (filter #(= (:type %) :coil) rung))))
                          program)
        rung-dependencies (mapv
                           (fn [rung-idx]
                             (let [contacts (nth contact-ids-by-rung rung-idx)]
                               (set
                                (for [contact contacts
                                      :when (contains? coil-ids contact)
                                      rung-dep-idx (range (count program))
                                      :when (contains? (nth coil-ids-by-rung rung-dep-idx) contact)]
                                  rung-dep-idx))))
                           (range (count program)))
        execution-order (loop [ordered []
                              remaining (set (range (count program)))]
                         (if (empty? remaining)
                           ordered
                           (let [next-rungs (filter
                                           (fn [rung-idx]
                                             (empty? (clojure.set/intersection
                                                     (get rung-dependencies rung-idx)
                                                     remaining)))
                                           remaining)]
                             (if (empty? next-rungs)
                               ;; Circular dependency detected, just add remaining rungs in order
                               (into ordered remaining)
                               (recur (into ordered next-rungs)
                                     (clojure.set/difference remaining (set next-rungs)))))))]
    execution-order))