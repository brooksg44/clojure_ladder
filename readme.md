# ClojureLadder

A Ladder Logic Simulator implemented in Clojure, inspired by ClassicLadder. ClojureLadder uses functional programming concepts, immutable data structures, and Clojure's powerful spec system for defining and validating ladder logic programs.

## Features

- **Pure functional implementation** using Clojure's immutable data structures
- **Declarative specifications** for ladder logic elements using clojure.spec
- **Interactive GUI** built with Quil (Processing)
- **Modbus interface** for connecting to real industrial equipment
- **IEC 61131-3 function blocks** for standardized PLC programming
- **Import/export capability** with ClassicLadder compatibility
- **Headless mode** for running as a standalone PLC without GUI

## Components

The simulator consists of several core components:

1. **Core Engine** - Handles the evaluation and simulation of ladder logic programs
2. **GUI Interface** - Allows editing and monitoring programs graphically
3. **PLC Runtime** - Provides real-time execution in an industrial control context
4. **I/O System** - For file operations, program import/export
5. **Utilities** - Helper functions for program manipulation and analysis

## Ladder Logic Elements

ClojureLadder supports the following ladder logic elements:

- **Inputs** - External input signals
- **Outputs** - External output signals
- **Contacts** - NO (Normally Open) and NC (Normally Closed)
- **Coils** - Internal relay outputs
- **Timers** - On-delay, off-delay, and pulse timers
- **Counters** - Up, down, and up/down counters

## Getting Started

### Prerequisites

- Java JDK 11+
- Clojure 1.11.0+
- Leiningen for building/running (or use the provided .jar)

### Installation

Clone the repository:

```bash
git clone https://github.com/brooksg44/clojure-ladder.git
cd clojure-ladder
```

Build with Leiningen:

```bash
lein uberjar
```

### Running the Application

Run in GUI mode:

```bash
java -jar target/clojure-ladder-0.1.0-standalone.jar
```

Run in headless mode with a program:

```bash
java -jar target/clojure-ladder-0.1.0-standalone.jar -f myprogram.edn -r
```

### Command Line Options

- `-p, --port PORT` - Port for web interface (default: 8080)
- `-m, --modbus-port PORT` - Port for Modbus TCP server (default: 502)
- `-f, --file FILE` - Ladder logic program file to load
- `-r, --run` - Start in run mode
- `-h, --help` - Show help

## Architecture

ClojureLadder uses a functional reactive approach to ladder logic simulation:

1. **Immutable Program State** - The program is represented as immutable data structures
2. **Pure Evaluation Functions** - Logic is evaluated using pure functions without side effects
3. **Reactive Updates** - Changes propagate through the system in a reactive way
4. **Declarative Specifications** - Elements are defined and validated using clojure.spec

### Data Model

Programs are structured as vectors of rungs, where each rung is a vector of elements:

```clojure
[
  ; Rung 1
  [{:id :input1 :type :input :x 40 :y 40 :state false}
   {:id :contact1 :type :contact :x 120 :y 40 :normally-open true}
   {:id :output1 :type :output :x 200 :y 40 :state false}]
  
  ; Rung 2
  [{:id :input2 :type :input :x 40 :y 120 :state false}
   {:id :timer1 :type :timer :x 120 :y 120 :preset 5 :current 0}]
]
```

## Example Programs

ClojureLadder comes with several example programs:

- Motor Start/Stop Circuit
- Timer Example
- Counter Example
- Traffic Light Control
- Conveyor System
- Sequential Process Control

Load an example in the GUI using the "Examples" menu.

## Usage in Headless Mode as a PLC

ClojureLadder can function as a real PLC by running in headless mode:

```bash
java -jar clojure-ladder.jar -f myprogram.edn -r -m 502
```

This starts the PLC runtime, loads your program, and enables the Modbus server on port 502. You can then connect to it using standard Modbus clients or SCADA systems.

## Development

### Project Structure

- `src/clojure_ladder/core.clj` - Core simulation engine
- `src/clojure_ladder/simulator.clj` - GUI interface
- `src/clojure_ladder/io.clj` - File I/O operations
- `src/clojure_ladder/plc.clj` - PLC runtime functionality
- `src/clojure_ladder/utils.clj` - Utility functions
- `src/clojure_ladder/examples.clj` - Example programs
- `src/clojure_ladder/main.clj` - Main application entry point

### Running Tests

```bash
lein test
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgements

- Inspired by [ClassicLadder](http://klasleren.sourceforge.net/), an open-source ladder logic simulator
- Built with [Quil](http://quil.info/), a Clojure wrapper for Processing
- Uses Clojure's spec library for program validation
