package com.iae.gui.controller;

import com.iae.model.Configuration;
import com.iae.model.Project;
import com.iae.persistence.ConfigurationRepository;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;


public class ProjectEditorController {


    @FXML private Label      dialogTitleLabel;

    // Project Details section
    @FXML private TextField  projectNameField;
    @FXML private ComboBox<Configuration> configurationCombo;
    @FXML private TextField  submissionsDirField;
    @FXML private TextField  commandLineArgumentsField;

    // Expected Output section
    @FXML private TextArea   expectedOutputArea;

    // Validation / buttons
    @FXML private Label      validationLabel;
    @FXML private Button     cancelButton;
    @SuppressWarnings("unused")
    @FXML private Button     saveButton;


    /** Gives access to configurations and allows creating new ones inline. */
    private ConfigurationRepository configurationRepository;

    /** Non-null when editing an existing project. */
    private Project editTarget;

    /** The created or updated Project after a successful save. */
    private Project result;

    /** true once the user has successfully clicked Save. */
    private boolean saved = false;


    @FXML
    private void initialize() {
        // Show only the configuration name in the combo
        configurationCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Configuration c) {
                return c == null ? "" : c.getName();
            }
            @Override
            public Configuration fromString(String s) {
                return configurationCombo.getItems().stream()
                        .filter(c -> c.getName().equals(s))
                        .findFirst().orElse(null);
            }
        });

        // Clear stale validation on any field change
        projectNameField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        configurationCombo.valueProperty().addListener((_o, _old, _n) -> clearValidation());
        submissionsDirField.textProperty().addListener((_o, _old, _n) -> clearValidation());
    }

    /**
     * Injects the ConfigurationRepository used to load/create configurations.
     * Must be called before the dialog is shown.
     */
    public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    public void initCreate(List<Configuration> availableConfigs) {
        editTarget = null;
        dialogTitleLabel.setText("New Project");
        populateConfigCombo(availableConfigs);
    }

    public void initEdit(Project project, List<Configuration> availableConfigs) {
        if (project == null) throw new IllegalArgumentException("project must not be null");
        editTarget = project;
        dialogTitleLabel.setText("Edit Project");

        populateConfigCombo(availableConfigs);

        projectNameField.setText(project.getProjectName());
        submissionsDirField.setText(nvl(project.getSubmissionsDirectoryPath()));
        commandLineArgumentsField.setText(nvl(project.getCommandLineArguments()));
        expectedOutputArea.setText(nvl(project.getExpectedOutput()));

        // Pre-select the configuration that the project is currently using
        if (project.getActiveConfiguration() != null) {
            configurationCombo.getItems().stream()
                    .filter(c -> c.getId().equals(project.getActiveConfiguration().getId()))
                    .findFirst()
                    .ifPresent(configurationCombo::setValue);
        }
    }


    /** Returns true when the user clicked Save and validation passed. */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Returns the newly-created or updatedProject,
     * or null if the dialog was cancelled.
     */
    public Project getResult() {
        return result;
    }


    /**
     * Opens the Config Editor dialog so the user can create a new Configuration
     * inline without leaving the Project Editor.
     */
    @FXML
    private void onNewConfiguration() {
        if (configurationRepository == null) {
            showAlert("No configuration repository is available.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/iae/gui/view/config_editor.fxml"));
            Parent root = loader.load();
            ConfigEditorController ctrl = loader.getController();
            ctrl.setRepository(configurationRepository);
            ctrl.initCreate();

            Stage dialog = new Stage();
            dialog.setTitle("New Configuration");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(cancelButton.getScene().getWindow());
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            if (ctrl.isSaved()) {
                Configuration newConfig = ctrl.getResult();
                configurationCombo.getItems().add(newConfig);
                configurationCombo.setValue(newConfig);
            }
        } catch (IOException e) {
            showAlert("Could not open Configuration Editor: " + e.getMessage());
        }
    }

    /** Opens a DirectoryChooser for the submissions folder. */
    @FXML
    private void onBrowseSubmissionsDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Submissions Directory");
        String current = submissionsDirField.getText();
        if (!current.isBlank()) {
            File initial = new File(current);
            if (initial.isDirectory()) dc.setInitialDirectory(initial);
        }
        File chosen = dc.showDialog(cancelButton.getScene().getWindow());
        if (chosen != null) {
            submissionsDirField.setText(chosen.getAbsolutePath());
        }
    }

    /** Loads the contents of a text file into the Expected Output area. */
    @FXML
    private void onLoadExpectedFromFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Expected Output");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text files", "*.txt", "*.out", "*.*"));
        File chosen = fc.showOpenDialog(cancelButton.getScene().getWindow());
        if (chosen != null) {
            try {
                String content = Files.readString(chosen.toPath());
                expectedOutputArea.setText(content);
            } catch (IOException e) {
                showAlert("Could not read file: " + e.getMessage());
            }
        }
    }

    /** Clears the Expected Output area. */
    @FXML
    private void onClearExpected() {
        expectedOutputArea.clear();
    }

    /** Validates the form, builds/updates the Project, then closes. */
    @FXML
    private void onSave() {
        if (!validate()) return;

        String name     = projectNameField.getText().strip();
        Configuration config = configurationCombo.getValue();
        String dir      = submissionsDirField.getText().strip();
        String args     = nullIfBlank(commandLineArgumentsField.getText());
        String expected = expectedOutputArea.getText();

        if (editTarget == null) {
            result = Project.newProject(name, config, dir, args, expected);
        } else {
            editTarget.setProjectName(name);
            editTarget.setActiveConfiguration(config);
            editTarget.setSubmissionsDirectoryPath(dir);
            editTarget.setCommandLineArguments(args);
            editTarget.setExpectedOutput(expected);
            result = editTarget;
        }

        saved = true;
        closeDialog();
    }

    /** Discards all changes and closes the dialog. */
    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }


    private void populateConfigCombo(List<Configuration> configs) {
        configurationCombo.getItems().setAll(configs);
    }

    /**
     * Validates required fields. Returns true if the form is valid;
     * shows an error message and returns false otherwise.
     */
    private boolean validate() {
        if (projectNameField.getText().isBlank()) {
            return showValidation("Project name is required.");
        }
        if (configurationCombo.getValue() == null) {
            return showValidation("A configuration must be selected.");
        }
        if (submissionsDirField.getText().isBlank()) {
            return showValidation("Submissions directory is required.");
        }
        File dir = new File(submissionsDirField.getText().strip());
        if (!dir.isDirectory()) {
            return showValidation("Submissions directory does not exist or is not a folder.");
        }
        return true;
    }

    /** Shows message in the red validation label and returns false. */
    private boolean showValidation(String message) {
        validationLabel.setText(message);
        return false;
    }

    private void clearValidation() {
        validationLabel.setText("");
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}