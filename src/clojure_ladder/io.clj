(ns clojure-ladder.io
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure-ladder.core :as core]))

;; ---- File Operations ----

(defn save-program
  "Saves the current program to a file"
  [filename program]
  (try
    (with-open [w (io/writer filename)]
      (binding [*print-length* nil
                *print-level* nil]
        (pr program w)))
    {:success true :message (str "Program saved to " filename)}
    (catch Exception e
      {:success false :message (str "Error saving program: " (.getMessage e))})))

(defn load-program
  "Loads a program from a file"
  [filename]
  (try
    (let [program (edn/read-string (slurp filename))]
      (if (s/valid? ::core/program program)
        {:success true :program program}
        {:success false :message "Invalid program format"}))
    (catch Exception e
      {:success false :message (str "Error loading program: " (.getMessage e))})))

;; ---- Export Operations ----

(defn export-to-text
  "Exports the program as a text description"
  [program]
  (with-out-str
    (println "ClojureLadder Program Export")
    (println "==========================")
    (println)
    (doseq [[rung-idx rung] (map-indexed vector program)]
      (println (str "Rung " (inc rung-idx) ":"))
      (doseq [element rung]
        (println (str "  - " (name (:type element)) 
                     ": " (name (:id element))
                     (when (contains? element :normally-open)
                       (str ", Normally " (if (:normally-open element) "Open" "Closed")))
                     (when (contains? element :preset)
                       (str ", Preset: " (:preset element))))))
      (println))))

;; Function to convert program to ClassicLadder format
;; This is a simple example and would need to be expanded
;; to fully support ClassicLadder's file format
(defn export-to-classic-ladder
  "Export program to a format compatible with ClassicLadder"
  [program]
  (with-out-str
    (println "#VER=2.0")
    (println "#LABELS")
    ;; Extract and print all variable labels
    (doseq [rung program
            element rung]
      (println (str (name (:id element)) "," (name (:type element)))))
    
    (println "#RUNGS")
    ;; This is simplified and would need more work to be fully compatible
    (doseq [[rung-idx rung] (map-indexed vector program)]
      (let [rung-elements (clojure.string/join " " 
                           (map #(str (name (:type %)) "," (name (:id %))) rung))]
        (println (str "RUNG" rung-idx "=" rung-elements))))))

;; ---- Import Operations ----

(defn parse-classic-ladder
  "Parse a ClassicLadder file format (simplified example)"
  [content]
  (let [lines (clojure.string/split content #"\r?\n")
        sections (group-by #(if (.startsWith % "#") (.substring % 1) "DATA") lines)
        labels (filter #(not (.startsWith % "#")) (get sections "LABELS" []))
        rungs (filter #(not (.startsWith % "#")) (get sections "RUNGS" []))]
    
    ;; Parse labels into a map of id -> type
    (let [label-map (into {} 
                     (map #(let [[id type] (clojure.string/split % #",")]
                             [(keyword id) (keyword type)])
                          labels))
          
          ;; Parse rungs (simplified)
          parsed-rungs (mapv
                        (fn [rung-line]
                          (let [[_ elements-str] (clojure.string/split rung-line #"=")
                                elements (clojure.string/split elements-str #" ")]
                            (mapv
                             (fn [element-str]
                               (let [[type id] (clojure.string/split element-str #",")
                                     id-kw (keyword id)
                                     type-kw (keyword type)
                                     x-pos (* (count elements) 80)] ; Simplified positioning
                                 (case type-kw
                                   :input (core/create-input id-kw x-pos 40 false)
                                   :output (core/create-output id-kw x-pos 40)
                                   :contact (core/create-contact id-kw x-pos 40 true)
                                   :coil (core/create-coil id-kw x-pos 40)
                                   :timer (core/create-timer id-kw x-pos 40 10)
                                   :counter (core/create-counter id-kw x-pos 40 5)
                                   nil)))
                             elements)))
                        rungs)]
      
      parsed-rungs)))

(defn import-from-classic-ladder
  "Import a program from ClassicLadder format"
  [filename]
  (try
    (let [content (slurp filename)
          program (parse-classic-ladder content)]
      {:success true :program program})
    (catch Exception e
      {:success false :message (str "Error importing program: " (.getMessage e))})))
