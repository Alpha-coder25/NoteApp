package com.example.noteapp.ui;

import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.exception.SettingsException;
import com.example.noteapp.model.AppSettings;
import com.example.noteapp.service.CategoryService;
import com.example.noteapp.service.SettingsService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Settings dialog: theme, auto-save delay, default category, application lock
 * (PIN), backup location and preferred export format. Persists through
 * {@link SettingsService}; never shows technical details to the user.
 */
public class SettingsView {

    private final SettingsService settingsService;
    private final CategoryService categoryService;
    private final Runnable themeCallback;

    public SettingsView(SettingsService settingsService, CategoryService categoryService, Runnable themeCallback) {
        this.settingsService = settingsService;
        this.categoryService = categoryService;
        this.themeCallback = themeCallback;
    }

    /** Opens the dialog; saves on OK. */
    public void showAndWait(javafx.stage.Window owner) {
        AppSettings settings = settingsService.getSettings();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Application settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().setAll("Light", "Dark");
        themeBox.getSelectionModel().select(settings.getTheme() == AppSettings.Theme.DARK ? "Dark" : "Light");

        TextField delayField = new TextField(String.valueOf(settings.getAutosaveDelaySeconds()));

        ComboBox<String> defaultCategoryBox = new ComboBox<>();
        defaultCategoryBox.getItems().add("(none)");
        try {
            categoryService.list().forEach(c -> defaultCategoryBox.getItems().add(c.getName()));
        } catch (Exception e) {
            // The category list is a convenience; never block settings on it.
        }
        String currentDefault = settings.getDefaultCategory().isBlank()
                ? "(none)" : settings.getDefaultCategory();
        defaultCategoryBox.getSelectionModel().select(
                defaultCategoryBox.getItems().contains(currentDefault) ? currentDefault : "(none)");

        TextField backupField = new TextField(settings.getBackupLocation());
        Button chooseBackup = new Button("Choose...");
        chooseBackup.setOnAction(e -> {
            var window = dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow();
            java.nio.file.Path folder = Dialogs.chooseDirectory(window, "Choose default backup folder", null);
            if (folder != null) backupField.setText(folder.toString());
        });

        ComboBox<String> exportFormatBox = new ComboBox<>();
        exportFormatBox.getItems().setAll("JSON", "TXT");
        exportFormatBox.getSelectionModel().select(settings.getExportFormat());

        // ----- application lock -------------------------------------------------
        PasswordField currentPin = new PasswordField();
        currentPin.setPromptText("Current PIN");
        PasswordField newPin = new PasswordField();
        newPin.setPromptText("New PIN (4-8 digits)");
        Label lockState = new Label(settings.isLockEnabled() ? "Lock is ON" : "Lock is OFF");
        Button enableLock = new Button(settings.isLockEnabled() ? "Change PIN" : "Enable lock");
        enableLock.setOnAction(e -> {
            try {
                if (settings.isLockEnabled() && !settingsService.verifyPin(currentPin.getText().strip())) {
                    Dialogs.warning("The current PIN is incorrect.");
                    return;
                }
                settingsService.enableLock(newPin.getText().strip());
                Dialogs.info("Application lock enabled.");
                lockState.setText("Lock is ON");
                currentPin.clear();
                newPin.clear();
            } catch (InvalidNoteException validationFailure) {
                Dialogs.warning(validationFailure.getMessage());
            } catch (SettingsException failure) {
                Dialogs.error("Lock settings", failure);
            }
        });
        Button disableLock = new Button("Disable lock");
        disableLock.setOnAction(e -> {
            try {
                if (!settingsService.verifyPin(currentPin.getText().strip())) {
                    Dialogs.warning("The current PIN is incorrect.");
                    return;
                }
                settingsService.disableLock();
                Dialogs.info("Application lock disabled.");
                lockState.setText("Lock is OFF");
                currentPin.clear();
                newPin.clear();
            } catch (SettingsException failure) {
                Dialogs.error("Lock settings", failure);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.add(new Label("Theme:"), 0, row); grid.add(themeBox, 1, row++);
        grid.add(new Label("Auto-save delay (seconds):"), 0, row); grid.add(delayField, 1, row++);
        grid.add(new Label("Default category:"), 0, row); grid.add(defaultCategoryBox, 1, row++);
        grid.add(new Label("Backup folder:"), 0, row); grid.add(new HBox(8, backupField, chooseBackup), 1, row++);
        grid.add(new Label("Preferred export format:"), 0, row); grid.add(exportFormatBox, 1, row++);
        grid.add(new Label("Application lock:"), 0, row);
        grid.add(new VBox(6, lockState, currentPin, newPin, new HBox(8, enableLock, disableLock)), 1, row++);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                int delay = Integer.parseInt(delayField.getText().strip());
                settings.setTheme("Dark".equals(themeBox.getValue()) ? AppSettings.Theme.DARK : AppSettings.Theme.LIGHT);
                settings.setAutosaveDelaySeconds(delay);
                String chosenCategory = defaultCategoryBox.getValue();
                settings.setDefaultCategory(chosenCategory == null || "(none)".equals(chosenCategory) ? "" : chosenCategory);
                settings.setBackupLocation(backupField.getText());
                settings.setExportFormat(exportFormatBox.getValue());
                settingsService.saveSettings(settings);
                if (themeCallback != null) {
                    themeCallback.run();
                }
            } catch (NumberFormatException e) {
                Dialogs.warning("Auto-save delay must be a whole number of seconds.");
            } catch (SettingsException e) {
                Dialogs.error("Settings", e);
            }
        }
    }
}
