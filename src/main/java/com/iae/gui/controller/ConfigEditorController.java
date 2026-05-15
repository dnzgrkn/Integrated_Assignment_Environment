package com.iae.gui.controller;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import com.iae.persistence.ConfigurationRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class ConfigEditorController {

    @FXML private Label      dialogTitleLabel;

    // General section
    @FXML private TextField  idField;
    @FXML private TextField  nameField;
    @FXML private ComboBox<LanguageType> languageTypeCombo;
    @FXML private CheckBox   compiledCheckBox;

    // Compiler section
    @FXML private TitledPane compilerPane;
    @FXML private TextField  compilerPathField;
    @FXML private Button     browseCompilerButton;
    @FXML private TextField  compilerFlagsField;

    // Execution section
    @FXML private TextField  runCommandField;
    @FXML private TextField  expectedSourceFileNameField;

    // Validation / buttons
    @FXML private Label      validationLabel;
    @FXML private Button     cancelButton;
    @SuppressWarnings("unused")
    @FXML private Button     saveButton;


    private ConfigurationRepository repository;

    private Configuration editTarget;

    private Configuration result;

    private boolean saved = false;


    @FXML
    private void initialize() {
        // Populate the language type combo from the LanguageType enum
        languageTypeCombo.getItems().setAll(LanguageType.values());

        // The compiler pane is only editable when "Compiled Language" is checked
        compilerPane.disableProperty().bind(compiledCheckBox.selectedProperty().not());

        // Clear stale validation messages on every field edit
        nameField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        compilerPathField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        runCommandField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        expectedSourceFileNameField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        languageTypeCombo.valueProperty().addListener((_o, _old, _n) -> clearValidation());
    }


    /**
     * Injects the ConfigurationRepository used to persist the result.
     * Must be called before the dialog is shown.
     */
    public void setRepository(ConfigurationRepository repository) {
        this.repository = repository;
    }

    /** Switches the dialog into create mode (blank form). */
    public void initCreate() {
        editTarget = null;
        dialogTitleLabel.setText("New Configuration");
        idField.setText("(auto-generated)");
    }


    public void initEdit(Configuration config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        editTarget = config;
        dialogTitleLabel.setText("Edit Configuration");

        idField.setText(config.getId());
        nameField.setText(config.getName());
        languageTypeCombo.setValue(config.getLanguageType());
        compiledCheckBox.setSelected(config.isCompiled());
        compilerPathField.setText(nvl(config.getCompilerPath()));
        compilerFlagsField.setText(nvl(config.getCompilerFlags()));
        runCommandField.setText(nvl(config.getRunCommand()));
        expectedSourceFileNameField.setText(nvl(config.getExpectedSourceFileName()));
    }


    /** Returns true when the user clicked Save and validation passed. */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Returns the newly-created or updated Configuration
     * or null if the dialog was cancelled.
     */
    public Configuration getResult() {
        return result;
    }


    /** Opens a file chooser to select the compiler executable. */
    @FXML
    private void onBrowseCompiler() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Compiler Executable");
        File chosen = fc.showOpenDialog(browseCompilerButton.getScene().getWindow());
        if (chosen != null) {
            compilerPathField.setText(chosen.getAbsolutePath());
        }
    }

    /** Validates the form, persists via the repository, then closes. */
    @FXML
    private void onSave() {
        if (!validate()) return;

        boolean isCompiled = compiledCheckBox.isSelected();

        if (editTarget == null) {
            result = Configuration.newConfiguration(
                    nameField.getText().strip(),
                    languageTypeCombo.getValue(),
                    isCompiled,
                    isCompiled ? compilerPathField.getText().strip() : null,
                    isCompiled ? nullIfBlank(compilerFlagsField.getText()) : null,
                    runCommandField.getText().strip(),
                    expectedSourceFileNameField.getText().strip()
            );
        } else {
            editTarget.setName(nameField.getText().strip());
            editTarget.setLanguageType(languageTypeCombo.getValue());
            editTarget.setCompiled(isCompiled);
            editTarget.setCompilerPath(isCompiled ? compilerPathField.getText().strip() : null);
            editTarget.setCompilerFlags(isCompiled ? nullIfBlank(compilerFlagsField.getText()) : null);
            editTarget.setRunCommand(runCommandField.getText().strip());
            editTarget.setExpectedSourceFileName(expectedSourceFileNameField.getText().strip());
            result = editTarget;
        }

        if (repository != null) {
            try {
                repository.save(result);
            } catch (IOException e) {
                showValidation("Could not save configuration: " + e.getMessage());
                return;
            }
        }

        saved = true;
        closeDialog();
    }

    /** Discards changes and closes without saving. */
    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }

    /**
     * Validates all required fields. Returns true if the form is valid;
     * sets the validation label and returns false otherwise.
     */
    private boolean validate() {
        if (nameField.getText().isBlank()) {
            return showValidation("Configuration name is required.");
        }
        if (languageTypeCombo.getValue() == null) {
            return showValidation("Language type is required.");
        }
        if (compiledCheckBox.isSelected() && compilerPathField.getText().isBlank()) {
            return showValidation("Compiler path is required for compiled languages.");
        }
        if (runCommandField.getText().isBlank()) {
            return showValidation("Run command is required.");
        }
        if (expectedSourceFileNameField.getText().isBlank()) {
            return showValidation("Source file name is required.");
        }
        return true;
    }

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
}