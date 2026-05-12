# IAE — Integrated Assignment Environment

IAE is a desktop application developed for CE 316 Programming Languages at İzmir Ekonomi Üniversitesi. It provides a unified environment for managing programming assignments: instructors can define assignments with expected outputs, students can submit source files in different languages, and the system compiles, executes, and compares results automatically.

## Team

- Deniz Gürkan
- Fatih Çelik
- Çağan Parlapan
- Can Esen
- Emre Taşkın

## Build & Run

```
mvn clean compile
mvn javafx:run
mvn test
```

### Demo workflow

After `mvn javafx:run`, the window opens with three regions:

- **Menu bar** — File / Edit / Help menus with full accelerators (Ctrl+N, Ctrl+O, Ctrl+S, F1, Alt+F4)
- **Toolbar** — disabled Run button on the left; progress bar and submission counter on the right
- **Split pane** — PROJECT panel (name, configuration, dirs, arguments, expected output, run summary) on the left; RESULTS table with four column headers on the right
- **Status bar** — shows "Ready" on startup; updates after each action

Key smoke-test clicks: Help → About (shows team / version), File → New Project (stub alert), Edit → Manage Configurations (stub alert), File → Open Project (file chooser → loads a `project_data.json`), Edit → Import Configuration (JSON file chooser), File → Exit (confirmation dialog).

## Project Structure

The codebase follows a four-layer architecture with strict downward dependencies:

- **Presentation** (`gui/`) — JavaFX controllers and FXML views
- **Service** (`service/`) — execution, output extraction, and comparison logic
- **Domain** (`model/`) — core entities (Assignment, Submission, TestCase, etc.)
- **Persistence** (`persistence/`) — JSON-backed storage via Jackson

## Status

Skeleton — milestone 2 (implementation) in progress.
