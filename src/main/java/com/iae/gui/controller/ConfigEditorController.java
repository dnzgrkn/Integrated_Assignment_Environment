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
import java.util.Objects;

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

    // Tracks the preset whose values are currently sitting in the form, so a
    // later language switch can tell "untouched preset default" apart from
    // "value the user actually typed" and overwrite only the former.
    private LanguagePreset lastAppliedPreset;


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

        languageTypeCombo.valueProperty().addListener((_o, _old, newLt) -> {
            clearValidation();
            applyLanguagePreset(newLt);
        });
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
        // Seed lastAppliedPreset with the persisted language so a later switch
        // will overwrite any field that still equals this language's default.
        lastAppliedPreset = LanguagePreset.forLanguage(config.getLanguageType());
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

    private void applyLanguagePreset(LanguageType lt) {
        LanguagePreset next = LanguagePreset.forLanguage(lt);
        if (next == null) {
            // OTHER — stop tracking a preset so user edits aren't overwritten later.
            lastAppliedPreset = null;
            return;
        }
        LanguagePreset prev = lastAppliedPreset;
        compiledCheckBox.setSelected(next.compiled());
        overwriteIfBlankOrPreset(compilerPathField,           next.compilerPath(),   prev != null ? prev.compilerPath()   : null);
        overwriteIfBlankOrPreset(compilerFlagsField,          next.compilerFlags(),  prev != null ? prev.compilerFlags()  : null);
        overwriteIfBlankOrPreset(runCommandField,             next.runCommand(),     prev != null ? prev.runCommand()     : null);
        overwriteIfBlankOrPreset(expectedSourceFileNameField, next.sourceFileName(), prev != null ? prev.sourceFileName() : null);
        lastAppliedPreset = next;
    }

    private static void overwriteIfBlankOrPreset(TextField field, String newValue, String previousPresetValue) {
        String current = field.getText();
        if (current == null || current.isBlank() || Objects.equals(current, previousPresetValue)) {
            field.setText(newValue);
        }
    }

    // Sensible defaults for the built-in languages. Picking C / Java / Python
    // toggles the compiled flag and fills any still-blank fields; OTHER is a
    // no-op so users can roll their own without being overwritten.
    private record LanguagePreset(boolean compiled,
                                  String compilerPath,
                                  String compilerFlags,
                                  String runCommand,
                                  String sourceFileName) {
        static LanguagePreset forLanguage(LanguageType lt) {
            if (lt == null) return null;
            return switch (lt) {
                case C      -> new LanguagePreset(true,  "gcc",   "-Wall -O2 -o main", "./main",          "main.c");
                case JAVA   -> new LanguagePreset(true,  "javac", "",                  "java -cp . Main", "Main.java");
                case PYTHON -> new LanguagePreset(false, "",      "",                  "python3 main.py", "main.py");
                case OTHER  -> null;
            };
        }
    }
}