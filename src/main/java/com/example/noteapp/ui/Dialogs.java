package com.example.noteapp.ui;

import com.example.noteapp.exception.AppException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Central place for user-facing dialogs (requirement: friendly messages in the
 * UI, technical details only in the log). All UI classes funnel error handling
 * through here so the tone and behavior stay consistent.
 */
public final class Dialogs {

    private static final Logger LOG = LoggerFactory.getLogger(Dialogs.class);

    private Dialogs() { }

    public static void info(String message) {
        alert(Alert.AlertType.INFORMATION, null, message);
    }

    public static void warning(String message) {
        alert(Alert.AlertType.WARNING, null, message);
    }

    /** Friendly error dialog; full details go to the log, not the screen. */
    public static void error(String title, Exception e) {
        if (e instanceof AppException appException) {
            // Our own exceptions already carry user-appropriate messages.
            alert(Alert.AlertType.ERROR, title, appException.getMessage());
        } else {
            alert(Alert.AlertType.ERROR, title,
                    "Something went wrong. Please try again.\n"
                            + "Technical details were written to the log.");
        }
        LOG.error(title, e);
    }

    public static void databaseError(Exception e) {
        error("Database error", e);
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(title);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setHeaderText(title == null ? type.name().charAt(0) + type.name().substring(1).toLowerCase() : title);
        alert.showAndWait();
    }

    // ------------------------------------------------------------------ choosers

    /** Save dialog with one extension filter per format ("JSON" -> *.json). */
    public static java.io.File saveFile(Window owner, String title, String initialName, List<String> formats) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialName);
        for (String format : formats) {
            String ext = format.toLowerCase();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(format + " files", "*." + ext));
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser.showSaveDialog(owner);
    }

    public static java.io.File openFile(Window owner, String title, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        for (String ext : extensions) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(ext.toUpperCase() + " files", "*." + ext));
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser.showOpenDialog(owner);
    }

    public static Path chooseDirectory(Window owner, String title, Path initialFolder) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (initialFolder != null && initialFolder.toFile().exists()) {
            chooser.setInitialDirectory(initialFolder.toFile());
        }
        java.io.File chosen = chooser.showDialog(owner);
        return chosen == null ? null : chosen.toPath();
    }
}
