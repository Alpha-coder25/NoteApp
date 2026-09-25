package com.example.noteapp;

import com.example.noteapp.repository.CategoryRepository;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.repository.NoteRepository;
import com.example.noteapp.repository.SettingsRepository;
import com.example.noteapp.repository.TagRepository;
import com.example.noteapp.service.BackupService;
import com.example.noteapp.service.CategoryService;
import com.example.noteapp.service.ExportService;
import com.example.noteapp.service.ImportService;
import com.example.noteapp.service.NoteService;
import com.example.noteapp.service.SearchService;
import com.example.noteapp.service.SettingsService;
import com.example.noteapp.service.TagService;
import com.example.noteapp.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Composition root: builds every layer (repository -> service -> UI) in one
 * place and passes dependencies downward (composition, no global state).
 * JavaFX {@code start} runs on the FX thread; the constructor only wires data.
 */
public class Main extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private DatabaseManager databaseManager;
    private SettingsService settingsService;
    private Exception initializationFailure;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        // Data-layer bootstrap before the window opens; failures are reported
        // by start() as a friendly dialog instead of a stack trace.
        try {
            databaseManager = new DatabaseManager();
            settingsService = new SettingsService(new SettingsRepository(databaseManager), databaseManager);
            LOG.info("NoteApp starting; data folder: {}", DatabaseManager.defaultDataDirectory());
        } catch (com.example.noteapp.exception.AppException e) {
            initializationFailure = e;
            LOG.error("Start-up failed", e);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        if (initializationFailure != null) {
            fatalDialog(initializationFailure);
            javafx.application.Platform.exit();
            return;
        }
        try {
            NoteRepository noteRepository = new NoteRepository(databaseManager);
            CategoryRepository categoryRepository = new CategoryRepository(databaseManager);
            TagRepository tagRepository = new TagRepository(databaseManager);

            NoteService noteService = new NoteService(noteRepository);
            CategoryService categoryService = new CategoryService(categoryRepository, noteRepository);
            TagService tagService = new TagService(tagRepository);
            SearchService searchService = new SearchService(noteRepository);
            ExportService exportService = new ExportService(noteRepository, categoryRepository);
            ImportService importService = new ImportService(noteRepository, categoryService);
            BackupService backupService = new BackupService(databaseManager);

            // Optional local lock screen before the main window.
            if (settingsService.isLockEnabled()) {
                if (!showLockScreen(primaryStage)) {
                    LOG.info("Startup cancelled at the lock screen");
                    javafx.application.Platform.exit();
                    return;
                }
            }

            MainWindow window = new MainWindow(noteService, categoryService, tagService,
                    searchService, exportService, importService, backupService, settingsService,
                    databaseManager);
            window.show(primaryStage);
        } catch (Exception e) {
            LOG.error("Fatal start-up failure", e);
            fatalDialog(e);
            System.exit(1);
        }
    }

    @Override
    public void stop() {
        if (databaseManager != null) {
            try {
                databaseManager.close();
            } catch (Exception e) {
                LOG.warn("Database did not close cleanly", e);
            }
        }
        LOG.info("NoteApp stopped");
    }

    /** Modal PIN prompt shown at start-up when the lock is enabled. */
    private boolean showLockScreen(Stage owner) {
        int attemptsLeft = 5;
        while (attemptsLeft > 0) {
            javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("NoteApp locked");
            dialog.setHeaderText("Enter your PIN (" + attemptsLeft + " attempt" + (attemptsLeft == 1 ? "" : "s") + " left)");
            dialog.setContentText("PIN:");
            javafx.scene.control.PasswordField pinField = new javafx.scene.control.PasswordField();
            pinField.setPromptText("4-8 digits");
            dialog.getDialogPane().setContent(pinField);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            javafx.application.Platform.runLater(pinField::requestFocus);
            dialog.setResultConverter(button -> button == ButtonType.OK ? pinField.getText() : null);
            Optional<String> entered = dialog.showAndWait();
            if (entered.isEmpty()) {
                return false; // user cancelled
            }
            try {
                if (settingsService.verifyPin(entered.get().strip())) {
                    return true;
                }
            } catch (Exception e) {
                LOG.warn("PIN verification error", e);
            }
            attemptsLeft--;
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText("Incorrect PIN");
            alert.setContentText(attemptsLeft > 0 ? "Please try again." : "Too many attempts. The application will close.");
            alert.showAndWait();
        }
        return false;
    }

    private void fatalDialog(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                "A fatal error prevented NoteApp from starting.\n"
                        + "Technical details have been written to the log.\n\n"
                        + e.getClass().getSimpleName() + ": " + e.getMessage(),
                ButtonType.CLOSE);
        alert.setHeaderText("NoteApp cannot start");
        alert.showAndWait();
    }
}
