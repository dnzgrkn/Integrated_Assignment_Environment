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

## Project Structure

The codebase follows a four-layer architecture with strict downward dependencies:

- **Presentation** (`gui/`) — JavaFX controllers and FXML views
- **Service** (`service/`) — execution, output extraction, and comparison logic
- **Domain** (`model/`) — core entities (Assignment, Submission, TestCase, etc.)
- **Persistence** (`persistence/`) — JSON-backed storage via Jackson

## Status

Skeleton — milestone 2 (implementation) in progress.
