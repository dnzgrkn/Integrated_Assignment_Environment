# Service Layer Implementation Spec — for Çağan Parlapan

> **Owner:** Çağan Parlapan
> **Target deadline:** Wednesday evening (May 13)
> **PR branch:** `feature/cagan-service-engine`
> **Reviewer:** Deniz Gürkan
> **Reference design:** Section 5.2, 7.2, 7.4, 9, 10 of the Milestone 1 Design Document

This document describes exactly what to implement in the `com.iae.service` package and its sub-packages. It is the contract between the service layer and the rest of the system; once the classes here are implemented, the GUI integration on Wednesday/Thursday should require no further changes to model or persistence.

---

## 0. Before you start

```powershell
git checkout master
git pull origin master
git checkout -b feature/cagan-service-engine
```

You will be writing into these (currently empty) directories:
- `src/main/java/com/iae/service/`
- `src/main/java/com/iae/service/extract/`
- `src/main/java/com/iae/service/execution/`
- `src/main/java/com/iae/service/compare/`
- `src/test/java/com/iae/service/`

Existing types you can rely on (do not modify them):
- `com.iae.model.Configuration` — has `getCompilerPath()`, `getCompilerFlags()`, `getRunCommand()`, `getExpectedSourceFileName()`, `isCompiled()`, `getLanguageType()`
- `com.iae.model.Project` — has `getActiveConfiguration()`, `getSubmissionsDirectoryPath()`, `getCommandLineArguments()`, `getExpectedOutput()`, `getResults()` (List<RunResult>)
- `com.iae.model.StudentSubmission` — `studentId`, `zipFilePath`, `extractedDirectoryPath`
- `com.iae.model.RunResult` — has nested `Status` enum: `PASS, FAIL, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT`. There are factory methods like `RunResult.pass(studentId, output)`, `RunResult.fail(studentId, output, message)` — check the file
- `com.iae.model.ComparisonResult` — has `success()` and `mismatch(lineNumber, expected, actual)` factory methods
- `com.iae.persistence.ConfigurationRepository` — may not be needed; ConfigurationManager wraps it

---

## 1. ZipExtractor

**File:** `src/main/java/com/iae/service/extract/ZipExtractor.java`
**Requirement:** R6
**Section in design doc:** 9.1

### Responsibility
Extract a single student ZIP file into a working directory using `java.util.zip`. No external `unzip` command (we can't assume it exists on the target machine).

### Public API
```java
public class ZipExtractor {
    public Path extract(Path zipFilePath, Path destinationDir) throws ExtractionException;
    public boolean validateStructure(Path extractedDir, String expectedFileName);
}
```

### `extract()` implementation
1. If `destinationDir` doesn't exist, create it (and all parents).
2. Open `ZipInputStream` over the file.
3. For each entry:
   - Compute target path: `destinationDir.resolve(entry.getName())`
   - **Zip Slip protection:** verify that `targetPath.toAbsolutePath().normalize()` starts with `destinationDir.toAbsolutePath().normalize()`. If not, throw `ExtractionException("Invalid ZIP entry path: " + entry.getName())`.
   - If entry is a directory → `Files.createDirectories(targetPath)`
   - Else → create parent dirs if needed, then copy stream bytes to target file using `Files.copy(zis, targetPath, REPLACE_EXISTING)`
4. Close stream (use try-with-resources).
5. Return `destinationDir`.

### `validateStructure()` implementation
- Walk the directory tree (max depth 2 is enough) and check if **any file** with the given name exists.
- Return `true` if found, `false` otherwise.
- Note: students often nest their code under an extra folder (e.g. `20240001/main.c` vs just `main.c`). Be lenient — search recursively up to 2 levels.

### Companion class
**File:** `src/main/java/com/iae/service/extract/ExtractionException.java`

```java
public class ExtractionException extends Exception {
    public ExtractionException(String message) { super(message); }
    public ExtractionException(String message, Throwable cause) { super(message, cause); }
}
```

### Tests to write
- `extract()` extracts a valid ZIP and returns the destination path
- `extract()` throws ExtractionException on a corrupt ZIP
- `extract()` blocks Zip Slip attempt (entry with `../../../evil.txt`)
- `validateStructure()` returns true when file exists at top level
- `validateStructure()` returns true when file is nested one level deep
- `validateStructure()` returns false when file missing

Use JUnit's `@TempDir` for the destination directory in tests.

---

## 2. OutputComparator

**File:** `src/main/java/com/iae/service/compare/OutputComparator.java`
**Requirement:** R8
**Section in design doc:** 9.4

### Responsibility
Compare a student program's captured stdout against the expected output, line by line, after trimming. No external `diff` or `fc`.

### Public API
```java
public class OutputComparator {
    public ComparisonResult compare(String actualOutput, String expectedOutput);
}
```

### Implementation
1. Normalize both inputs:
   - Split by `\\r?\\n`
   - For each line: trim trailing whitespace
   - Remove trailing empty lines (lines at the end of the list that are empty after trim)
2. Loop from `i = 0` to `max(actual.length, expected.length) - 1`:
   - If either list is shorter, treat the missing line as `null`
   - If `!Objects.equals(actualLine, expectedLine)` → return `ComparisonResult.mismatch(i + 1, expectedLine, actualLine)` (1-based line number)
3. If loop completes → return `ComparisonResult.success()`

### Tests to write
- Identical outputs → success
- Different on line 3 → mismatch with lineNumber=3
- Actual has more lines than expected → mismatch
- Different line endings (CRLF vs LF) → still success
- Trailing whitespace differences → still success
- Trailing empty lines → still success

---

## 3. CommandRunner

**File:** `src/main/java/com/iae/service/execution/CommandRunner.java`
**Requirements:** R7
**Section in design doc:** 5.2.3, 9.2

### Responsibility
Run external processes (compilers and student executables) using `ProcessBuilder`. Capture stdout and stderr. Enforce a timeout. This is **the most failure-prone class in the project** — be paranoid.

### Public API
```java
public class CommandRunner {

    public static class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;
        public CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) { ... }
    }

    public CommandResult runProcess(List<String> command, Path workingDir, int timeoutSeconds);

    public CommandResult compile(Configuration config, Path sourceDir, int compileTimeoutSeconds);

    public CommandResult execute(Configuration config, Path workingDir, String argumentsLine, int runTimeoutSeconds);
}
```

### `runProcess()` — the core method

```java
ProcessBuilder pb = new ProcessBuilder(command);
pb.directory(workingDir.toFile());
pb.redirectErrorStream(false);  // keep stderr separate
Process process = pb.start();

ExecutorService executor = Executors.newFixedThreadPool(2);
Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
if (!finished) {
    process.destroyForcibly();
    process.waitFor(5, TimeUnit.SECONDS);  // grace period
    executor.shutdownNow();
    return new CommandResult(-1, "", "", true);
}

int exit = process.exitValue();
String out = stdoutFuture.get(2, TimeUnit.SECONDS);
String err = stderrFuture.get(2, TimeUnit.SECONDS);
executor.shutdown();
return new CommandResult(exit, out, err, false);
```

`readStream()` reads UTF-8 lines until EOF and joins them with `\n`. Use `BufferedReader` + `InputStreamReader(stream, StandardCharsets.UTF_8)`.

### `compile()` implementation
- If `!config.isCompiled()` → return `new CommandResult(0, "", "", false)` (no-op)
- Build command list:
  - Start: `config.getCompilerPath()` (e.g., "gcc")
  - Split `config.getCompilerFlags()` by whitespace (e.g., "-Wall -o main" → ["-Wall", "-o", "main"]) — only if non-blank
  - Append `config.getExpectedSourceFileName()` (e.g., "main.c")
- Call `runProcess(command, sourceDir, compileTimeoutSeconds)`

### `execute()` implementation
- Build command list from `config.getRunCommand()`:
  - For compiled languages, this is typically the produced executable, e.g. `./main` on Linux or `main.exe` on Windows. On Windows you may need to handle this: if `runCommand` starts with `./`, strip it and append `.exe` (only do this on Windows — check `System.getProperty("os.name").toLowerCase().contains("win")`)
  - For interpreted languages, it's typically `python main.py` or similar
- Split `runCommand` by whitespace
- Append all tokens from `argumentsLine` (split by whitespace; this is the user-supplied command-line args)
- Call `runProcess(command, workingDir, runTimeoutSeconds)`

### Tests to write
- Run a simple `cmd /c echo hello` (or `echo hello` on Unix) → exitCode 0, stdout contains "hello"
- Timeout enforcement: run `cmd /c ping -n 100 127.0.0.1` (or `sleep 100`) with 2-second timeout → timedOut=true
- Non-zero exit code captured correctly
- stderr captured separately from stdout

Note: write tests in a platform-aware way (skip Linux-only or Windows-only commands depending on OS).

---

## 4. ProjectRunner

**File:** `src/main/java/com/iae/service/ProjectRunner.java`
**Requirements:** R6, R7, R8
**Section in design doc:** 5.2.1, 7.2, 7.4, 9.5

### Responsibility
Orchestrate the full batch evaluation pipeline. For each ZIP in the project's submissions directory: extract → compile (if needed) → execute → compare → record result. **Per-submission error isolation:** one bad submission must not kill the whole batch.

### Public API
```java
public class ProjectRunner {

    public interface RunListener {
        void onSubmissionStarted(String studentId);
        void onSubmissionCompleted(RunResult result);
        void onProgress(int completed, int total);
        void onAllCompleted();
    }

    public ProjectRunner(Project project);
    public ProjectRunner(Project project, int compileTimeoutSeconds, int runTimeoutSeconds);

    public void addListener(RunListener listener);
    public void removeListener(RunListener listener);

    public List<RunResult> runAll() throws IOException;
}
```

The two-argument constructor exists so the GUI can pass user-configured timeouts later. The single-argument constructor uses defaults: 30s compile, 10s run.

### `runAll()` implementation

```java
List<RunResult> results = new ArrayList<>();
Path submissionsDir = Paths.get(project.getSubmissionsDirectoryPath());
List<Path> zipFiles = listZips(submissionsDir);  // sorted, .zip only
int total = zipFiles.size();

for (int i = 0; i < total; i++) {
    Path zip = zipFiles.get(i);
    String studentId = stripZipExtension(zip.getFileName().toString());
    notifyStarted(studentId);

    RunResult result;
    try {
        result = processSingleSubmission(studentId, zip);
    } catch (Throwable t) {
        // Safety net for any unexpected error — never let the batch die.
        result = RunResult.error(studentId, Status.RUNTIME_ERROR,
            "Unexpected error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    results.add(result);
    project.getResults().add(result);  // also accumulate into the project
    notifyCompleted(result);
    notifyProgress(i + 1, total);
}

notifyAllCompleted();
return results;
```

### `processSingleSubmission()` — the per-student pipeline

This is the heart of the system. Decision tree:

```
1. Extract ZIP into a temp working dir (e.g., %TEMP%/iae/<uuid>/<studentId>/)
   - On ExtractionException → return RunResult(COMPILE_ERROR, "Extraction failed: ...")

2. Validate that the expected source file exists in the extracted tree
   - If not → return RunResult(COMPILE_ERROR, "Source file <name> not found")

3. If config.isCompiled():
   a. CommandRunner.compile(config, sourceDir, 30s)
   b. If timedOut → return RunResult(COMPILE_ERROR, "Compilation timed out")
   c. If exitCode != 0 → return RunResult(COMPILE_ERROR, stderr)

4. CommandRunner.execute(config, workingDir, project.getCommandLineArguments(), 10s)
   a. If timedOut → return RunResult(TIMEOUT, "Process exceeded 10s limit")
   b. If exitCode != 0 → return RunResult(RUNTIME_ERROR, stderr)

5. OutputComparator.compare(execResult.stdout, project.getExpectedOutput())
   - If success → return RunResult.pass(studentId, stdout)
   - If mismatch → return RunResult.fail(studentId, stdout,
       "Expected line " + lineNumber + ": " + expected + " | got: " + actual)

6. After all of the above, clean up the temp working directory (delete recursively).
   Failure to clean up is logged but doesn't affect the result.
```

### Notes on implementation
- Temp working dir: `Path workDir = Files.createTempDirectory("iae-" + studentId + "-");`
- `validateStructure` returns boolean. If false, you also want to know the file name — pass `config.getExpectedSourceFileName()` to the message.
- The "sourceDir" passed to `compile()` and `execute()` should be the directory **containing the source file**, not necessarily the extraction root (since students may nest). Find the source file's actual parent directory.
- Listener notifications must be safe to call from a background thread (they may dispatch to `Platform.runLater()` inside) — but **CommandRunner does not need to be on a background thread by itself**; it's the GUI's job to wrap `runAll()` in `Task<>`. You just call listeners synchronously.

### Tests to write
- Mock-based test: pass a project pointing to a dir with 3 fake ZIPs (Emre's fixtures), verify that 3 RunResults come back
- Compile error in one submission doesn't stop the batch
- Listener callbacks fire in the expected order

---

## 5. ConfigurationManager

**File:** `src/main/java/com/iae/service/ConfigurationManager.java`
**Requirement:** R4
**Section in design doc:** 5.2.6

### Responsibility
Singleton in-memory registry of configurations, backed by `ConfigurationRepository`. The GUI talks to this; this talks to the repository.

### Public API
```java
public class ConfigurationManager {
    public static ConfigurationManager getInstance();
    public static synchronized void resetForTesting(ConfigurationRepository repo);  // visible for tests

    public List<Configuration> getAll();
    public Optional<Configuration> getById(String id);
    public void save(Configuration config) throws IOException;
    public boolean delete(String id) throws IOException;
    public Configuration importConfiguration(Path source) throws IOException;
    public void exportConfiguration(Configuration config, Path destination) throws IOException;
    public void refresh() throws IOException;  // reload from disk
}
```

### Implementation
- Private static instance, lazily initialized in `getInstance()`
- Constructor takes a `ConfigurationRepository` (default: `new ConfigurationRepository(ConfigurationRepository.defaultDirectory())`)
- In-memory cache: `Map<String, Configuration>` keyed by id, populated by `refresh()` on first access
- All write methods (`save`, `delete`, import) update both the cache and the disk via the repository
- `resetForTesting()` allows tests to inject a different repo with `@TempDir`

### Tests to write
- save → getAll() contains it
- delete → getAll() doesn't contain it
- importConfiguration → loaded config is in getAll()
- Singleton behavior (two getInstance calls return same reference, before reset)

---

## Commit strategy

Make 8-10 commits as you go. Suggested sequence:

1. `feat(service): add ExtractionException`
2. `feat(service): add ZipExtractor with Zip Slip protection`
3. `test(service): add ZipExtractor tests`
4. `feat(service): add OutputComparator`
5. `test(service): add OutputComparator tests`
6. `feat(service): add CommandRunner with timeout enforcement`
7. `test(service): add CommandRunner tests`
8. `feat(service): add ConfigurationManager singleton`
9. `feat(service): add ProjectRunner with batch loop and listener interface`
10. `test(service): add ProjectRunner integration test`

After all commits:
```powershell
mvn clean test     # all tests (yours + the 30 existing) must pass
git push -u origin feature/cagan-service-engine
```

Then open a PR on GitHub against master, with Deniz as reviewer.

---

## Things you can ask Deniz about
- If you're stuck on Windows-specific process execution issues
- If a model class is missing a factory method you need (we can add it)
- If the listener interface needs a different signature for the GUI integration

Don't block on these — leave a TODO in the code and ping me.