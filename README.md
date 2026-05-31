# Integrated Assignment Environment (IAE)

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-GUI-474A54?style=for-the-badge&logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

> **CE 316 - Software Engineering Project** | Izmir University of Economics
> **Team 8:**
- Deniz Gürkan
- Fatih Çelik
- Çağan Parlapan
- Can Esen
- Emre Taşkın


---

## Project Overview
The **Integrated Assignment Environment (IAE)** is a desktop application developed for CE 316 Programming Languages at İzmir Ekonomi Üniversitesi. It provides a unified environment for managing programming assignments: instructors can define assignments with expected outputs, students can submit source files in different languages, and the system compiles, executes, and compares results automatically.

IAE is fundamentally **language-agnostic**. Whether it's a compiled language like C and Java, or an interpreted language like Python, the system seamlessly evaluates the code as long as the host machine has the necessary compilers installed.

---

## Key Features & The Evaluation Pipeline

IAE operates on a four-stage background pipeline:

1. **Extraction:** Securely unzips student submissions in complete isolation.
2. **Compilation:** Dynamically invokes local compilers based on the assigned rule-set (bypassed for interpreted languages).
3. **Execution:** Runs the binary with a strict **10-second timeout** to prevent system crashes from infinite loops or memory leaks.
4. **Comparison:** Aggressively trims trailing whitespaces and performs a line-by-line comparison against the expected lecturer output.

### Visual Feedback (Color-Coded Verdicts)
- 🟢 **PASS:** Compiled, executed on time, and matched perfectly.
- 🟠 **FAIL:** Executed successfully, but output deviated.
- 🔴 **COMPILE_ERROR / RUNTIME_ERROR:** Syntax failure, corrupted ZIP, or runtime crash.
- 🟣 **TIMEOUT:** Program entered an infinite loop and was forcefully terminated.

---

## System Architecture

The software is strictly decoupled using a **4-Layer Architecture**, ensuring maintainability and separation of concerns:

- **Presentation Layer (JavaFX):** Handles user interactions, updates the results table, and keeps the UI responsive using background worker threads (`Task<V>`).
- **Service Layer:** The core evaluation engine. Manages the pipeline (`ZipExtractor`, `CommandRunner`, `OutputComparator`).
- **Domain Layer:** Contains the core models (`Project`, `Configuration`, `RunResult`).
- **Persistence Layer:** Manages portable JSON serialization using the Jackson library, allowing lecturers to easily import/export grading environments.

---

## Technology Stack
- **Language:** Java 21
- **UI Framework:** JavaFX (with custom CSS styling)
- **Build Tool:** Apache Maven
- **Data Persistence:** Jackson JSON Processor
- **Architecture:** Layered MVC

---

## Getting Started

### Prerequisites
- JDK 21 or higher installed and set in your `PATH`.
- Apache Maven installed.
- Required compilers (e.g., `gcc`, `python`) installed in your system `PATH` for the languages you intend to evaluate.

### Installation & Execution

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/your-username/iae-project.git](https://github.com/your-username/iae-project.git)
   cd iae-project
2. **Build the project using Maven:**
   ```bash
   mvn clean install
3. **Run the application:**
**Navigate to the target directory and execute the fat JAR:**
   ```bash
   cd target
   java -jar IAE-1.0.0-jar-with-dependencies.jar

## App Workflow

After running the application, the window opens with three regions:

- *Menu bar* — File / Edit / Help menus with full accelerators (Ctrl+N, Ctrl+O, Ctrl+S, F1, Alt+F4)
- *Toolbar* — disabled Run button on the left; progress bar and submission counter on the right
- *Split pane* — PROJECT panel (name, configuration, dirs, arguments, expected output, run summary) on the left; RESULTS table with four column headers on the right
- *Status bar* — shows "Ready" on startup; updates after each action

Key smoke-test clicks: Help → About (shows team / version), File → New Project (stub alert), Edit → Manage Configurations (stub alert), File → Open Project (file chooser → loads a project_data.json), Edit → Import Configuration (JSON file chooser), File → Exit (confirmation dialog).

## Running an evaluation

1. Edit → Manage Configurations → create a configuration (e.g., for C)
2. File → New Project → fill in name, choose the configuration, browse to a submissions directory containing student ZIP files, fill in command-line arguments and expected output, Save
3. File → Save Project As → choose where to store project_data.json
4. Toolbar → Run
5. Results stream into the table as each submission is evaluated; final summary shown in the status bar.
6. File → Save Project at any time to persist results.

## Status

**Completed.** Delivered for CE 316 Milestone 3 Final Evaluation.
