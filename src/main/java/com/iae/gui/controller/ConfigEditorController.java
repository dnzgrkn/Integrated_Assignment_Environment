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
    @FXML private Button     exportButton;
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
        languageTypeCombo.getItems().setAll(LanguageType.values());

        // The compiler pane is editable only when "Compiled Language" is checked.
        compilerPane.disableProperty().bind(compiledCheckBox.selectedProperty().not());

        nameField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        compilerPathField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        runCommandField.textProperty().addListener((_o, _old, _n) -> clearValidation());
        expectedSourceFileNameField.textProperty().addListener((_o, _old, _n) -> clearValidation());

        languageTypeCombo.valueProperty().addListener((_o, _old, newLt) -> {
            clearValidation();
            applyLanguagePreset(newLt);
        });
    }


    public void setRepository(ConfigurationRepository repository) {
        this.repository = repository;
    }

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
        // Setting the language fires the preset listener; setting the other fields
        // afterwards lets the persisted values win over preset defaults.
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


    public boolean isSaved() {
        return saved;
    }

    public Configuration getResult() {
        return result;
    }


    @FXML
    private void onBrowseCompiler() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Compiler Executable");
        File chosen = fc.showOpenDialog(browseCompilerButton.getScene().getWindow());
        if (chosen != null) {
            compilerPathField.setText(chosen.getAbsolutePath());
        }
    }

    @FXML
    private void onSave() {
        if (!validate()) return;

        Configuration built = buildFromForm();
        if (repository != null) {
            try {
                repository.save(built);
            } catch (IOException e) {
                showValidation("Could not save configuration: " + e.getMessage());
                return;
            }
        }
        // Reflect saved values back onto the editTarget reference so callers
        // still holding it see the update without reloading from disk.
        if (editTarget != null) {
            copyValues(built, editTarget);
            result = editTarget;
        } else {
            result = built;
        }
        saved = true;
        closeDialog();
    }

    @FXML
    private void onExport() {
        if (!validate()) return;

        Configuration built = buildFromForm();
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Configuration");
        fc.setInitialFileName(built.getName() + ".json");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
        File dest = fc.showSaveDialog(exportButton.getScene().getWindow());
        if (dest == null) return;

        try {
            ConfigurationRepository repo = repository != null
                    ? repository
                    : new ConfigurationRepository(dest.toPath().getParent());
            repo.exportToFile(built, dest.toPath());
        } catch (IOException e) {
            showValidation("Could not export: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }

    private Configuration buildFromForm() {
        boolean isCompiled = compiledCheckBox.isSelected();
        String name        = nameField.getText().strip();
        LanguageType lt    = languageTypeCombo.getValue();
        String compPath    = isCompiled ? compilerPathField.getText().strip() : null;
        String compFlags   = isCompiled ? nullIfBlank(compilerFlagsField.getText()) : null;
        String runCmd      = runCommandField.getText().strip();
        String srcName     = expectedSourceFileNameField.getText().strip();

        if (editTarget == null) {
            return Configuration.newConfiguration(name, lt, isCompiled, compPath, compFlags, runCmd, srcName);
        }
        return new Configuration(editTarget.getId(), name, lt, isCompiled, compPath, compFlags, runCmd, srcName);
    }

    private static void copyValues(Configuration from, Configuration to) {
        to.setName(from.getName());
        to.setLanguageType(from.getLanguageType());
        to.setCompiled(from.isCompiled());
        to.setCompilerPath(from.getCompilerPath());
        to.setCompilerFlags(from.getCompilerFlags());
        to.setRunCommand(from.getRunCommand());
        to.setExpectedSourceFileName(from.getExpectedSourceFileName());
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
        overwriteIfBlankOrPreset(compilerPathField,           next.compilerPath(),    prev != null ? prev.compilerPath()    : null);
        overwriteIfBlankOrPreset(compilerFlagsField,          next.compilerFlags(),   prev != null ? prev.compilerFlags()   : null);
        overwriteIfBlankOrPreset(runCommandField,             next.runCommand(),      prev != null ? prev.runCommand()      : null);
        overwriteIfBlankOrPreset(expectedSourceFileNameField, next.sourceFileName(),  prev != null ? prev.sourceFileName()  : null);
        lastAppliedPreset = next;
    }

    private static void overwriteIfBlankOrPreset(TextField field, String newValue, String previousPresetValue) {
        String current = field.getText();
        if (current == null || current.isBlank() || Objects.equals(current, previousPresetValue)) {
            field.setText(newValue);
        }
    }

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
