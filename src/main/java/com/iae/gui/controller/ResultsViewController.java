package com.iae.gui.controller;

import com.iae.model.Project;
import com.iae.model.RunResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;


/**
 * Controller for results_view.fxml.
 * <p>
 * Covers Requirement R9 (display results of each student file).
 * Read-only — does not mutate the project.
 * <p>
 * Load with:
 * <pre>
 *   FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/iae/gui/view/results_view.fxml"));
 *   Parent root = loader.load();
 *   ResultsViewController ctrl = loader.getController();
 *   ctrl.initWithProject(project);
 *   Stage stage = new Stage();
 *   stage.setTitle("Results — " + project.getProjectName());
 *   stage.setScene(new Scene(root));
 *   stage.show();
 * </pre>
 */
public class ResultsViewController {

    private static final String COLOR_PASS          = "#d4edda";
    private static final String COLOR_FAIL          = "#fff3cd";
    private static final String COLOR_COMPILE_ERROR = "#f8d7da";
    private static final String COLOR_RUNTIME_ERROR = "#fde8d8";
    private static final String COLOR_TIMEOUT       = "#e8d5f5";


    @FXML private Label projectNameLabel;

    // Summary toolbar counts
    @FXML private Label passCountLabel;
    @FXML private Label failCountLabel;
    @FXML private Label compileErrorCountLabel;
    @FXML private Label runtimeErrorCountLabel;
    @FXML private Label timeoutCountLabel;

    // Results table
    @FXML private TableView<RunResult> resultsTable;
    @FXML private TableColumn<RunResult, String>          studentIdColumn;
    @FXML private TableColumn<RunResult, RunResult.Status> statusColumn;
    @FXML private TableColumn<RunResult, String>          capturedOutputColumn;
    @FXML private TableColumn<RunResult, String>          errorMessageColumn;

    // Detail pane
    @FXML private Label   detailStudentId;
    @FXML private Label   detailStatus;
    @FXML private TextArea detailCapturedOutput;
    @FXML private TextArea detailErrorMessage;

    @SuppressWarnings("unused")
    private Project project;
    private final ObservableList<RunResult> items = FXCollections.observableArrayList();


    @FXML
    public void initialize() {
        // Wire column value factories
        studentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        capturedOutputColumn.setCellValueFactory(new PropertyValueFactory<>("capturedOutput"));
        errorMessageColumn.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));

        // Truncate long text in cells, full content shown in detail pane below
        capturedOutputColumn.setCellFactory(col -> truncatingCell());
        errorMessageColumn.setCellFactory(col -> truncatingCell());

        // Status column: show status name with a coloured background
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(RunResult.Status status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status.name());
                    String bg = statusColor(status);
                    setStyle("-fx-background-color: " + bg + "; -fx-font-weight: bold;");
                }
            }
        });

        // Row-level colour for quick scanning
        resultsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(RunResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    String bg = statusColor(item.getStatus());
                    // Subtle tint — not as strong as the status cell
                    setStyle("-fx-background-color: " + bg + "80;"); // 80 = ~50% alpha hex
                }
            }
        });

        resultsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, selected) -> showDetail(selected));

        resultsTable.setItems(items);

        // Clear detail pane initially
        clearDetail();
    }

//    public void initWithProject(Project project) {
//        if (project == null) throw new IllegalArgumentException("project must not be null");
//       this.project = project;
//
//        projectNameLabel.setText(project.getProjectName());
//
//        List<RunResult> results = project.getResults();
//        if (results != null) {
//            items.setAll(results);
//        }
//
//       refreshSummaryCounts();
//
//       // Auto-select first row for convenience
//        if (!items.isEmpty()) {
//            resultsTable.getSelectionModel().selectFirst();
//        }
//    }



    @FXML
    private void onExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Results as CSV");
        String defaultName = project != null
                ? project.getProjectName().replaceAll("\\s+", "_") + "_results.csv"
                : "results.csv";
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));

        File dest = chooser.showSaveDialog(getStage());
        if (dest == null) return;

        try (PrintWriter pw = new PrintWriter(dest, StandardCharsets.UTF_8)) {
            pw.println("StudentID,Status,CapturedOutput,ErrorMessage");
            for (RunResult r : items) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        escape(r.getStudentId()),
                        r.getStatus() != null ? r.getStatus().name() : "",
                        escape(firstLine(r.getCapturedOutput())),
                        escape(r.getErrorMessage()));
            }
        } catch (IOException e) {
            System.err.println("CSV export failed: " + e.getMessage());
        }
    }

    /** Closes this window. */
    @FXML
    private void onClose() {
        getStage().close();
    }


    /** Re-computes and displays the per-status counts in the toolbar. */
    @SuppressWarnings("unused")
    private void refreshSummaryCounts() {
        long pass    = count(RunResult.Status.PASS);
        long fail    = count(RunResult.Status.FAIL);
        long compile = count(RunResult.Status.COMPILE_ERROR);
        long runtime = count(RunResult.Status.RUNTIME_ERROR);
        long timeout = count(RunResult.Status.TIMEOUT);

        passCountLabel.setText(pass    + " PASS");
        failCountLabel.setText(fail    + " FAIL");
        compileErrorCountLabel.setText(compile + " COMPILE_ERROR");
        runtimeErrorCountLabel.setText(runtime + " RUNTIME_ERROR");
        timeoutCountLabel.setText(timeout + " TIMEOUT");
    }

    private long count(RunResult.Status status) {
        return items.stream().filter(r -> r.getStatus() == status).count();
    }

    /** Fills the detail pane with the data from the selected result. */
    private void showDetail(RunResult result) {
        if (result == null) {
            clearDetail();
            return;
        }
        detailStudentId.setText(nvl(result.getStudentId()));
        String statusName = result.getStatus() != null ? result.getStatus().name() : "—";
        detailStatus.setText(statusName);
        detailStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                + statusTextColor(result.getStatus()) + ";");
        detailCapturedOutput.setText(nvl(result.getCapturedOutput()));
        detailErrorMessage.setText(nvl(result.getErrorMessage()));
    }

    private void clearDetail() {
        detailStudentId.setText("—");
        detailStatus.setText("—");
        detailStatus.setStyle("");
        detailCapturedOutput.setText("");
        detailErrorMessage.setText("");
    }

    /** Returns the background colour hex for a given status. */
    private String statusColor(RunResult.Status status) {
        if (status == null) return "transparent";
        return switch (status) {
            case PASS          -> COLOR_PASS;
            case FAIL          -> COLOR_FAIL;
            case COMPILE_ERROR -> COLOR_COMPILE_ERROR;
            case RUNTIME_ERROR -> COLOR_RUNTIME_ERROR;
            case TIMEOUT       -> COLOR_TIMEOUT;
        };
    }

    /** Returns the foreground colour hex for the detail status label. */
    private String statusTextColor(RunResult.Status status) {
        if (status == null) return "black";
        return switch (status) {
            case PASS          -> "#155724";
            case FAIL          -> "#856404";
            case COMPILE_ERROR -> "#721c24";
            case RUNTIME_ERROR -> "#c05800";
            case TIMEOUT       -> "#6f42c1";
        };
    }

    /** Returns a cell factory that truncates long text with an ellipsis. */
    private TableCell<RunResult, String> truncatingCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setText(null);
                } else {
                    // Show only the first non-blank line, max 80 chars
                    String first = firstLine(text);
                    setText(first.length() > 80 ? first.substring(0, 77) + "…" : first);
                }
            }
        };
    }

    /** Returns the first non-blank line of {@code s}, or "" if none. */
    private static String firstLine(String s) {
        if (s == null || s.isBlank()) return "";
        return s.lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElse("");
    }

    /** Escapes double-quotes for CSV. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"").replace("\n", " ").replace("\r", "");
    }

    private Stage getStage() {
        return (Stage) resultsTable.getScene().getWindow();
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}