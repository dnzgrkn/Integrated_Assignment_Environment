package com.iae.gui.controller;

import com.iae.model.Configuration;
import com.iae.model.Project;
import com.iae.model.RunResult;
import com.iae.persistence.ConfigurationRepository;
import com.iae.persistence.ProjectRepository;
import com.iae.service.ProjectRunner;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainController {

    // Menu items
    @FXML private MenuItem menuNewProject;
    @FXML private MenuItem menuOpenProject;
    @FXML private MenuItem menuSaveProject;
    @FXML private MenuItem menuSaveProjectAs;
    @FXML private MenuItem menuExit;
    @FXML private MenuItem menuEditProject;
    @FXML private MenuItem menuManageConfigs;
    @FXML private MenuItem menuImportConfig;
    @FXML private MenuItem menuExportConfig;
    @FXML private MenuItem menuManual;
    @FXML private MenuItem menuAbout;

    // Toolbar
    @FXML private Button runButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    // Left panel — project info
    @FXML private Label lblProjectName;
    @FXML private Label lblConfigName;
    @FXML private Label lblSubsDir;
    @FXML private Label lblArgs;
    @FXML private Label lblExpected;

    // Left panel — run summary
    @FXML private Label lblTotal;
    @FXML private Label lblPassed;
    @FXML private Label lblFailed;
    @FXML private Label lblPending;

    // Results table
    @FXML private TableView<RunResult> resultsTable;
    @FXML private TableColumn<RunResult, String> colStudentId;
    @FXML private TableColumn<RunResult, RunResult.Status> colStatus;
    @FXML private TableColumn<RunResult, String> colCapturedOutput;
    @FXML private TableColumn<RunResult, String> colErrorMessage;

    // Status bar
    @FXML private Label statusBar;

    // State
    private Project currentProject;
    private Path currentProjectDir;

    private final ConfigurationRepository configRepo =
            new ConfigurationRepository(ConfigurationRepository.defaultDirectory());
    private final ProjectRepository projectRepo = new ProjectRepository();

    @FXML
    public void initialize() {
        initResultsTable();
        setStatus("Ready");
        refreshUiState();
    }

    private void initResultsTable() {
        colStudentId.setCellValueFactory(new PropertyValueFactory<>("studentId"));

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(RunResult.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    switch (item) {
                        case PASS -> setStyle("-fx-background-color: #c8e6c9;");
                        case FAIL -> setStyle("-fx-background-color: #ffe0b2;");
                        default   -> setStyle("-fx-background-color: #ffcdd2;");
                    }
                }
            }
        });

        colCapturedOutput.setCellValueFactory(new PropertyValueFactory<>("capturedOutput"));
        colCapturedOutput.setCellFactory(col -> truncatingCell());

        colErrorMessage.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));
        colErrorMessage.setCellFactory(col -> truncatingCell());
    }

    private static TableCell<RunResult, String> truncatingCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String display = item.length() > 80 ? item.substring(0, 80) + "..." : item;
                    setText(display);
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    @FXML
    private void onNewProject() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/iae/gui/view/project_editor.fxml")
            );

            Scene scene = new Scene(loader.load());

            ProjectEditorController controller =
                    loader.getController();

            controller.setConfigurationRepository(configRepo);

            controller.initCreate(configRepo.loadAll());

            Stage stage = new Stage();
            stage.setTitle("New Project");
            stage.setScene(scene);
            stage.showAndWait();

            if (controller.isSaved()) {

                currentProject = controller.getResult();

                refreshUiState();

                setStatus("Created project: "
                        + currentProject.getProjectName());
            }

        } catch (IOException e) {

            showError("New Project Failed", e.getMessage());
        }
    }

    @FXML
    private void onOpenProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Project files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(getStage());
        if (file == null) return;

        try {
            currentProject = projectRepo.loadProject(Paths.get(file.toString()));
            currentProjectDir = file.toPath().getParent();
            refreshUiState();
            setStatus("Opened: " + currentProject.getProjectName());
        } catch (IOException e) {
            showError("Open Project Failed", e.getMessage());
        }
    }

    @FXML
    private void onSaveProject() {
        if (currentProject == null) return;
        if (currentProjectDir != null) {
            try {
                projectRepo.saveProject(currentProject, currentProjectDir);
                setStatus("Saved: " + currentProject.getProjectName());
            } catch (IOException e) {
                showError("Save Failed", e.getMessage());
            }
        } else {
            onSaveProjectAs();
        }
    }

    @FXML
    private void onSaveProjectAs() {
        if (currentProject == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose project directory");
        File dir = chooser.showDialog(getStage());
        if (dir == null) return;

        currentProjectDir = dir.toPath();
        try {
            projectRepo.saveProject(currentProject, currentProjectDir);
            setStatus("Saved: " + currentProject.getProjectName());
            refreshUiState();
        } catch (IOException e) {
            showError("Save Failed", e.getMessage());
        }
    }

    @FXML
    private void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Exit IAE");
        confirm.setHeaderText(null);
        confirm.setContentText("Exit IAE? Unsaved changes will be lost.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                Platform.exit();
                System.exit(0);
            }
        });
    }

    @FXML
    private void onEditProject() {

        if (currentProject == null) {
            showError("Edit Project", "No project is open.");
            return;
        }

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/iae/gui/view/project_editor.fxml")
            );

            Scene scene = new Scene(loader.load());

            ProjectEditorController controller =
                    loader.getController();

            controller.setConfigurationRepository(configRepo);

            controller.initEdit(
                    currentProject,
                    configRepo.loadAll()
            );

            Stage stage = new Stage();
            stage.setTitle("Edit Project");
            stage.setScene(scene);
            stage.showAndWait();

            if (controller.isSaved()) {

                currentProject = controller.getResult();

                refreshUiState();

                setStatus("Edited project: "
                        + currentProject.getProjectName());
            }

        } catch (IOException e) {

            showError("Edit Project Failed", e.getMessage());
        }
    }

    @FXML
    private void onManageConfigurations() {

        ChoiceDialog<String> modeDialog = new ChoiceDialog<>(
                "Create New Configuration",
                List.of(
                        "Create New Configuration",
                        "Edit Existing Configuration"
                )
        );

        modeDialog.setTitle("Manage Configurations");
        modeDialog.setHeaderText("Choose an action");
        modeDialog.setContentText("Mode:");

        modeDialog.showAndWait().ifPresent(choice -> {

            if (choice.equals("Create New Configuration")) {

                openCreateConfigurationDialog();

            } else {

                openEditConfigurationDialog();
            }
        });
    }

    private void openCreateConfigurationDialog() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/iae/gui/view/config_editor.fxml")
            );

            Scene scene = new Scene(loader.load());

            ConfigEditorController controller =
                    loader.getController();

            controller.setRepository(configRepo);

            controller.initCreate();

            Stage stage = new Stage();
            stage.setTitle("New Configuration");
            stage.setScene(scene);
            stage.showAndWait();

            if (controller.isSaved()) {

                setStatus("Created configuration: "
                        + controller.getResult().getName());
            }

        } catch (IOException e) {

            showError("Configuration Error", e.getMessage());
        }
    }

    private void openEditConfigurationDialog() {

        try {

            List<Configuration> configs = configRepo.loadAll();

            if (configs.isEmpty()) {

                showInfo(
                        "No Configurations",
                        null,
                        "There are no configurations to edit."
                );

                return;
            }

            ChoiceDialog<Configuration> pickDialog =
                    new ChoiceDialog<>(configs.get(0), configs);

            pickDialog.setTitle("Edit Configuration");
            pickDialog.setHeaderText("Select a configuration");
            pickDialog.setContentText("Configuration:");

            pickDialog.showAndWait().ifPresent(config -> {

                try {

                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource(
                                    "/com/iae/gui/view/config_editor.fxml")
                    );

                    Scene scene = new Scene(loader.load());

                    ConfigEditorController controller =
                            loader.getController();

                    controller.setRepository(configRepo);

                    controller.initEdit(config);

                    Stage stage = new Stage();
                    stage.setTitle("Edit Configuration");
                    stage.setScene(scene);
                    stage.showAndWait();

                    if (controller.isSaved()) {

                        setStatus("Edited configuration: "
                                + controller.getResult().getName());
                    }

                } catch (IOException e) {

                    showError("Configuration Error", e.getMessage());
                }
            });

        } catch (IOException e) {

            showError("Load Configurations Failed", e.getMessage());
        }
    }

    @FXML
    private void onImportConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(getStage());
        if (file == null) return;

        try {
            Configuration imported = configRepo.importFromFile(file.toPath());
            showInfo("Import Successful", null, "Configuration imported: " + imported.getName());
            setStatus("Configuration imported: " + imported.getName());
        } catch (IOException e) {
            showError("Import Failed", e.getMessage());
        }
    }

    @FXML
    private void onExportConfiguration() {
        List<Configuration> configs;
        try {
            configs = configRepo.loadAll();
        } catch (IOException e) {
            showError("Export Failed", e.getMessage());
            return;
        }

        if (configs.isEmpty()) {
            showInfo("Export Configuration", null, "No configurations to export.");
            return;
        }

        ChoiceDialog<Configuration> pick = new ChoiceDialog<>(configs.get(0), configs);
        pick.setTitle("Export Configuration");
        pick.setHeaderText("Select a configuration to export:");
        pick.setContentText("Configuration:");
        pick.showAndWait().ifPresent(chosen -> {
            FileChooser saveChooser = new FileChooser();
            saveChooser.setTitle("Export Configuration");
            saveChooser.setInitialFileName(chosen.getName() + ".json");
            saveChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
            File dest = saveChooser.showSaveDialog(getStage());
            if (dest == null) return;

            try {
                configRepo.exportToFile(chosen, dest.toPath());
                setStatus("Configuration exported: " + chosen.getName());
            } catch (IOException e) {
                showError("Export Failed", e.getMessage());
            }
        });
    }

    @FXML
    private void onManual() {
        URL manualUrl = getClass().getResource("/com/iae/help/manual.html");
        if (manualUrl == null) {
            showInfo("IAE — User Manual", null,
                    "User manual will be available in the final release.");
            return;
        }
        Stage manualStage = new Stage();
        manualStage.setTitle("IAE — User Manual");
        manualStage.setWidth(800);
        manualStage.setHeight(600);
        manualStage.setResizable(true);
        WebView webView = new WebView();
        webView.getEngine().load(manualUrl.toExternalForm());
        manualStage.setScene(new Scene(webView));
        manualStage.show();
    }

    @FXML
    private void onAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About IAE");
        about.setHeaderText("Integrated Assignment Environment");
        about.setContentText(
                "Version: 1.0.0-SNAPSHOT\n" +
                "Course: CE 316 — Programming Languages, Spring 2026\n\n" +
                "Team 8:\n" +
                "  Fatih \u00c7elik\n" +
                "  \u00c7a\u011fan Parlapan\n" +
                "  Can Esen\n" +
                "  Deniz G\u00fcrkan\n" +
                "  Emre Ta\u015fk\u0131n\n\n" +
                "University: Izmir University of Economics");
        about.showAndWait();
    }

    @FXML
    private void onRunClicked() {
        if (currentProject == null) return;

        // Pre-flight validation
        String subsDirStr = currentProject.getSubmissionsDirectoryPath();
        if (subsDirStr == null || subsDirStr.isBlank()) {
            showError("Run Failed", "Submissions directory is not configured.");
            return;
        }
        if (!Files.isDirectory(Paths.get(subsDirStr))) {
            showError("Run Failed", "Submissions directory does not exist: " + subsDirStr);
            return;
        }
        if (currentProject.getActiveConfiguration() == null) {
            showError("Run Failed", "No configuration selected for this project.");
            return;
        }
        if (currentProject.getExpectedOutput() == null || currentProject.getExpectedOutput().isBlank()) {
            setStatus("Warning: expected output is blank — proceeding.");
        }

        // Clear previous run state
        resultsTable.getItems().clear();
        currentProject.getResults().clear();
        progressBar.setProgress(0);
        progressLabel.setText("0 / 0 submissions");
        lblTotal.setText("0");
        lblPassed.setText("0");
        lblFailed.setText("0");
        lblPending.setText("0");
        setStatus("Running...");
        runButton.setDisable(true);

        Task<List<RunResult>> task = new Task<>() {
            @Override
            protected List<RunResult> call() throws Exception {
                ProjectRunner runner = new ProjectRunner(currentProject);
                runner.addListener(new ProjectRunner.RunListener() {
                    @Override
                    public void onSubmissionStarted(String studentId) {
                        Platform.runLater(() -> setStatus("Processing " + studentId + "..."));
                    }

                    @Override
                    public void onSubmissionCompleted(RunResult result) {
                        Platform.runLater(() -> {
                            resultsTable.getItems().add(result);
                            refreshRunSummary();
                        });
                    }

                    @Override
                    public void onProgress(int completed, int total) {
                        Platform.runLater(() -> {
                            progressBar.setProgress((double) completed / total);
                            progressLabel.setText(completed + " / " + total + " submissions");
                        });
                    }

                    @Override
                    public void onAllCompleted() {
                        Platform.runLater(() -> {
                            runButton.setDisable(false);
                            List<RunResult> results = currentProject.getResults();
                            long passed = results.stream()
                                    .filter(r -> r.getStatus() == RunResult.Status.PASS).count();
                            long failed = results.stream()
                                    .filter(r -> r.getStatus() == RunResult.Status.FAIL).count();
                            long errors = results.stream()
                                    .filter(r -> r.getStatus() != RunResult.Status.PASS
                                              && r.getStatus() != RunResult.Status.FAIL).count();
                            setStatus("Run complete: " + passed + " passed, " + failed + " failed, "
                                    + errors + " error out of " + results.size() + " submissions");
                        });
                    }
                });
                return runner.runAll();
            }
        };

        task.setOnFailed(e -> {
            runButton.setDisable(false);
            Throwable ex = task.getException();
            showError("Run Failed", ex != null ? ex.getMessage() : "Unknown error");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void refreshRunSummary() {
        if (currentProject == null) {
            lblTotal.setText("0");
            lblPassed.setText("0");
            lblFailed.setText("0");
            lblPending.setText("0");
            return;
        }
        List<RunResult> results = currentProject.getResults();
        int total = results != null ? results.size() : 0;
        long passed = results != null
                ? results.stream().filter(r -> r.getStatus() == RunResult.Status.PASS).count()
                : 0;
        lblTotal.setText(String.valueOf(total));
        lblPassed.setText(String.valueOf(passed));
        lblFailed.setText(String.valueOf(total - passed));
        lblPending.setText("0");
    }

    private void refreshUiState() {
        if (currentProject == null) {
            lblProjectName.setText("—");
            lblConfigName.setText("—");
            lblSubsDir.setText("—");
            lblArgs.setText("—");
            lblExpected.setText("—");
            lblTotal.setText("0");
            lblPassed.setText("0");
            lblFailed.setText("0");
            lblPending.setText("0");
        } else {
            lblProjectName.setText(nvl(currentProject.getProjectName()));
            Configuration cfg = currentProject.getActiveConfiguration();
            lblConfigName.setText(cfg != null ? nvl(cfg.getName()) : "—");
            lblSubsDir.setText(nvl(currentProject.getSubmissionsDirectoryPath()));
            lblArgs.setText(nvl(currentProject.getCommandLineArguments()));
            lblExpected.setText(nvl(currentProject.getExpectedOutput()));

            List<RunResult> results = currentProject.getResults();
            int total = results != null ? results.size() : 0;
            long passed = results != null
                    ? results.stream().filter(r -> r.getStatus() == RunResult.Status.PASS).count()
                    : 0;
            long failed = total - passed;
            lblTotal.setText(String.valueOf(total));
            lblPassed.setText(String.valueOf(passed));
            lblFailed.setText(String.valueOf(failed));
            lblPending.setText("0");
        }

        boolean hasProject = currentProject != null;
        menuSaveProject.setDisable(!hasProject);
        menuSaveProjectAs.setDisable(!hasProject);
        menuEditProject.setDisable(!hasProject);
        runButton.setDisable(!hasProject);
    }

    private void setStatus(String msg) {
        statusBar.setText(msg);
    }

    private Stage getStage() {
        return (Stage) statusBar.getScene().getWindow();
    }

    private String nvl(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }

    private void showInfo(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
