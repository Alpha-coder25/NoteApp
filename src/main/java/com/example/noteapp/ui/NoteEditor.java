package com.example.noteapp.ui;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.model.Note;
import com.example.noteapp.service.NoteService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Right-hand editor pane: title, category, tags, content, pin/archive/delete
 * buttons and a save-state indicator ("Saving..." / "Saved").
 *
 * <p><b>Auto-save:</b> a debounce timer persists edits {@code delay} seconds
 * after the last keystroke - never per keystroke. Edits live in the widget text
 * and the local {@link Note} first; if a save fails the text stays in the
 * editor and the user is told their content was not discarded (no silent data
 * loss).
 */
public class NoteEditor {

    private static final String STATE_IDLE = "";
    private static final String STATE_SAVING = "Saving...";
    private static final String STATE_SAVED = "Saved";
    private static final String STATE_SAVE_FAILED = "Unable to save. Your content has not been discarded.";

    private final BorderPane root = new BorderPane();
    private final TextField titleField = new TextField();
    private final javafx.scene.control.TextArea contentArea = new javafx.scene.control.TextArea();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextField tagField = new TextField();
    private final FlowPane tagChips = new FlowPane(4, 4);
    private final Label saveState = new Label(STATE_IDLE);
    private final Label timestampsLabel = new Label();
    private final Button pinButton = new Button("Pin");
    private final Button archiveButton = new Button("Archive");
    private final Button deleteButton = new Button("\u2715 Delete");

    private NoteService noteService;
    private Consumer<Void> onNotesChanged;
    private Consumer<String> onError;
    private IntSupplier delaySecondsSupplier = () -> 2;

    private Note currentNote;
    private boolean loading;
    private Timeline autosaveTimer;
    private boolean built;

    /** Wires services; called once by MainWindow before the editor is shown. */
    public void attach(NoteService noteService, com.example.noteapp.service.CategoryService categoryService) {
        this.noteService = noteService;
        buildUi();
    }

    /** Where the auto-save delay (seconds) comes from - settings-backed. */
    public void setDelaySecondsSupplier(IntSupplier supplier) {
        this.delaySecondsSupplier = supplier == null ? () -> 2 : supplier;
    }

    public Node getRoot() {
        return root;
    }

    private void buildUi() {
        if (built) {
            return;
        }
        built = true;

        titleField.setPromptText("Note title");
        titleField.getStyleClass().add("editor-title");
        contentArea.setWrapText(true);
        contentArea.setPromptText("Write your note here...");
        saveState.getStyleClass().add("save-state");

        pinButton.setTooltip(new Tooltip("Pin this note (Ctrl+Shift+P)"));
        pinButton.setOnAction(e -> togglePin());
        archiveButton.setOnAction(e -> toggleArchive());
        deleteButton.getStyleClass().add("danger");
        deleteButton.setOnAction(e -> requestDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, pinButton, archiveButton, deleteButton, spacer, saveState);
        actions.setPadding(new Insets(4, 0, 4, 0));
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        tagField.setPromptText("Add tag and press Enter");
        tagField.setDisable(true);
        tagField.setOnAction(e -> addTagFromField());

        categoryBox.setPromptText("Category");
        categoryBox.setDisable(true);

        HBox meta = new HBox(8, categoryBox, tagField);
        HBox.setHgrow(tagField, Priority.ALWAYS);
        meta.setPadding(new Insets(4, 0, 4, 0));

        VBox top = new VBox(4, titleField, meta, tagChips, actions);
        top.setPadding(new Insets(8));

        timestampsLabel.getStyleClass().add("note-meta");
        root.setTop(top);
        root.setCenter(contentArea);
        root.setBottom(timestampsLabel);
        BorderPane.setMargin(timestampsLabel, new Insets(4, 8, 8, 8));

        // Debounced auto-save: restart the timer on every edit.
        titleField.textProperty().addListener((obs, oldV, newV) -> scheduleAutosave());
        contentArea.textProperty().addListener((obs, oldV, newV) -> scheduleAutosave());
    }

    /**
     * Opens a note in the editor.
     *
     * @param onNotesChanged run after any change that alters the note list
     * @param onError run with a user-facing message when an operation fails
     */
    public void open(Note note, Consumer<Void> onNotesChanged, Consumer<String> onError) {
        this.onNotesChanged = onNotesChanged;
        this.onError = onError;
        this.currentNote = note;
        loading = true;
        titleField.setText(note.getTitle());
        contentArea.setText(note.getContent());
        categoryBox.getItems().setAll("Uncategorized");
        categoryBox.getSelectionModel().select(0);
        tagChips.getChildren().clear();
        note.getTags().forEach(this::addChip);
        tagField.clear();
        tagField.setDisable(false);
        categoryBox.setDisable(false);
        pinButton.setText(note.isPinned() ? "Unpin" : "Pin");
        archiveButton.setText(note.isArchived() ? "Unarchive" : "Archive");
        timestampsLabel.setText("Created: " + com.example.noteapp.util.DateUtil.toUi(note.getCreatedAt())
                + "    Modified: " + com.example.noteapp.util.DateUtil.toUi(note.getUpdatedAt()));
        saveState.setText(STATE_IDLE);
        loading = false;
    }

    private void addChip(String tag) {
        Label chip = new Label(tag + "  \u2715");
        chip.getStyleClass().add("tag-chip");
        chip.setOnMouseClicked(e -> removeTag(tag));
        tagChips.getChildren().add(chip);
    }

    private void addTagFromField() {
        if (currentNote == null) return;
        String tag = tagField.getText();
        if (tag == null || tag.isBlank()) return;
        try {
            currentNote.addTag(tag);   // validation happens inside the model
            tagField.clear();
            addChip(tag.strip().toLowerCase());
            scheduleAutosave();
        } catch (InvalidNoteException e) {
            if (onError != null) onError.accept(e.getMessage());
        }
    }

    private void removeTag(String tag) {
        if (currentNote == null) return;
        currentNote.removeTag(tag);
        tagChips.getChildren().removeIf(node ->
                node instanceof Label label && label.getText().startsWith(tag + "  "));
        scheduleAutosave();
    }

    private void togglePin() {
        if (currentNote == null) return;
        try {
            boolean newState = !currentNote.isPinned();
            noteService.setPinned(currentNote.getId(), newState);
            currentNote.setPinned(newState);
            pinButton.setText(newState ? "Unpin" : "Pin");
            if (onNotesChanged != null) onNotesChanged.accept(null);
        } catch (com.example.noteapp.exception.AppException e) {
            if (onError != null) onError.accept("Could not update the pin state. Please try again.");
        }
    }

    private void toggleArchive() {
        if (currentNote == null) return;
        try {
            boolean newState = !currentNote.isArchived();
            noteService.setArchived(currentNote.getId(), newState);
            currentNote.setArchived(newState);
            archiveButton.setText(newState ? "Unarchive" : "Archive");
            if (onNotesChanged != null) onNotesChanged.accept(null);
        } catch (com.example.noteapp.exception.AppException e) {
            if (onError != null) onError.accept("Could not update the archive state. Please try again.");
        }
    }

    private void requestDelete() {
        if (currentNote == null) return;
        if (Dialogs.confirm("Move to trash", "Move \"" + currentNote.getTitle() + "\" to the trash?")) {
            try {
                noteService.moveToTrash(currentNote.getId());
                close();
                if (onNotesChanged != null) onNotesChanged.accept(null);
            } catch (com.example.noteapp.exception.AppException e) {
                if (onError != null) onError.accept("Could not move the note to the trash. Please try again.");
            }
        }
    }

    private void scheduleAutosave() {
        if (loading || currentNote == null) return;
        if (autosaveTimer != null) {
            autosaveTimer.stop();
        }
        saveState.setText(STATE_SAVING);
        autosaveTimer = new Timeline(new KeyFrame(
                Duration.seconds(Math.max(1, delaySecondsSupplier.getAsInt())),
                event -> saveNow()));
        autosaveTimer.setCycleCount(1);
        autosaveTimer.play();
    }

    /**
     * Saves immediately; also bound to Ctrl+S.
     *
     * @return true when there was nothing to save or the save succeeded
     */
    public boolean saveNow() {
        if (autosaveTimer != null) {
            autosaveTimer.stop();
        }
        if (currentNote == null) {
            return true;
        }
        try {
            currentNote.setTitle(titleField.getText());
            currentNote.setContent(contentArea.getText());
            noteService.updateNote(currentNote);
            saveState.setText(STATE_SAVED);
            timestampsLabel.setText("Created: " + com.example.noteapp.util.DateUtil.toUi(currentNote.getCreatedAt())
                    + "    Modified: " + com.example.noteapp.util.DateUtil.toUi(currentNote.getUpdatedAt()));
            if (onNotesChanged != null) onNotesChanged.accept(null);
            return true;
        } catch (InvalidNoteException e) {
            saveState.setText(STATE_SAVE_FAILED);
            if (onError != null) onError.accept(e.getMessage());
            return false;
        } catch (com.example.noteapp.exception.AppException | RuntimeException e) {
            // Content stays in the editor; nothing was lost.
            saveState.setText(STATE_SAVE_FAILED);
            if (onError != null) {
                onError.accept("Unable to save changes.\nYour current content has not been discarded.");
            }
            return false;
        }
    }

    /** True when the editor holds edits not yet persisted. */
    public boolean hasUnsavedChanges() {
        return currentNote != null && autosaveTimer != null
                && autosaveTimer.getStatus() == javafx.animation.Animation.Status.RUNNING;
    }

    /** Clears the editor (after delete/close). */
    public void close() {
        if (autosaveTimer != null) {
            autosaveTimer.stop();
        }
        currentNote = null;
        titleField.clear();
        contentArea.clear();
        tagChips.getChildren().clear();
        timestampsLabel.setText("");
        saveState.setText(STATE_IDLE);
        tagField.setDisable(true);
        categoryBox.setDisable(true);
    }
}
