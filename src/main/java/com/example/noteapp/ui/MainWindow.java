package com.example.noteapp.ui;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.model.AppSettings;
import com.example.noteapp.model.Category;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.service.BackupService;
import com.example.noteapp.service.CategoryService;
import com.example.noteapp.service.ExportService;
import com.example.noteapp.service.ImportService;
import com.example.noteapp.service.NoteService;
import com.example.noteapp.service.SearchService;
import com.example.noteapp.service.SettingsService;
import com.example.noteapp.service.TagService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The main three-pane window (sidebar / note list / editor) with menu bar,
 * search, sort controls and keyboard shortcuts. Pure view logic: every action
 * delegates to a service - no SQL and no business rules here (layered design).
 */
public class MainWindow {

    private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);

    private final NoteService noteService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final SearchService searchService;
    private final ExportService exportService;
    private final ImportService importService;
    private final BackupService backupService;
    private final SettingsService settingsService;
    private final DatabaseManager databaseManager;

    /** Which listing is currently shown. */
    private enum View { ALL, PINNED, ARCHIVE, TRASH, CATEGORY, TAG }

    private View currentView = View.ALL;
    private Long selectedCategoryId;
    private String selectedTag;
    private final ObservableList<Note> notes = FXCollections.observableArrayList();
    private Note selectedNote;

    private final NoteEditor editor = new NoteEditor();

    private Stage stage;
    private Scene scene;
    private TextField searchField;
    private ComboBox<String> sortBox;
    private ListView<Note> noteListView;
    private VBox editorContainer;
    private Label editorPlaceholder;
    private final VBox sidebarCategoriesBox = new VBox(2);
    private final VBox sidebarTagsBox = new VBox(2);

    public MainWindow(NoteService noteService, CategoryService categoryService, TagService tagService,
                      SearchService searchService, ExportService exportService, ImportService importService,
                      BackupService backupService, SettingsService settingsService,
                      DatabaseManager databaseManager) {
        this.noteService = noteService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.searchService = searchService;
        this.exportService = exportService;
        this.importService = importService;
        this.backupService = backupService;
        this.settingsService = settingsService;
        this.databaseManager = databaseManager;

        editor.attach(noteService, categoryService);
        editor.setDelaySecondsSupplier(() -> settingsService.getSettings().getAutosaveDelaySeconds());
    }

    // ================================================================== layout

    public void show(Stage primaryStage) {
        this.stage = primaryStage;
        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildCenter());

        scene = new Scene(root, 1100, 700);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> { searchField.requestFocus(); searchField.selectAll(); });
        applyTheme();

        primaryStage.setTitle("NoteApp - Offline Notes");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            if (!confirmUnsavedChanges()) {
                event.consume();
            }
        });
        primaryStage.show();
        refreshSidebar();
        refreshNotes();
    }

    private Node buildTopBar() {
        searchField = new TextField();
        searchField.setPromptText("Search... (Ctrl+F)");
        searchField.textProperty().addListener((obs, oldText, newText) -> refreshNotes());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        sortBox = new ComboBox<>(FXCollections.observableArrayList(
                "Last modified first", "Oldest modified first", "Newest first",
                "Oldest first", "Title A-Z", "Title Z-A"));
        sortBox.getSelectionModel().selectFirst();
        sortBox.valueProperty().addListener((obs, oldVal, newVal) -> refreshNotes());
        sortBox.setTooltip(new Tooltip("Sort order"));

        Button settingsButton = new Button("\u2699");
        settingsButton.setTooltip(new Tooltip("Settings"));
        settingsButton.setOnAction(e -> showSettings());

        HBox top = new HBox(10, buildMenuBar(), searchField, sortBox, settingsButton);
        top.setPadding(new Insets(6));
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return top;
    }

    private MenuBar buildMenuBar() {
        MenuItem newNote = new MenuItem("New Note");
        newNote.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newNote.setOnAction(e -> createNewNote());
        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveItem.setOnAction(e -> editor.saveNow());
        MenuItem exportItem = new MenuItem("Export...");
        exportItem.setOnAction(e -> exportNotes());
        MenuItem importItem = new MenuItem("Import...");
        importItem.setOnAction(e -> importNotes());
        MenuItem backupItem = new MenuItem("Backup...");
        backupItem.setOnAction(e -> doBackup());
        MenuItem restoreItem = new MenuItem("Restore...");
        restoreItem.setOnAction(e -> doRestore());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> {
            if (confirmUnsavedChanges()) {
                stage.close();
            }
        });
        Menu fileMenu = new Menu("_File", null, newNote, saveItem, new SeparatorMenuItem(),
                exportItem, importItem, new SeparatorMenuItem(), backupItem, restoreItem,
                new SeparatorMenuItem(), exitItem);

        CheckMenuItem pinItem = new CheckMenuItem("Pin Note");
        pinItem.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        pinItem.setOnAction(e -> togglePin());
        CheckMenuItem archiveItem = new CheckMenuItem("Archive Note");
        archiveItem.setOnAction(e -> toggleArchive());
        MenuItem deleteItem = new MenuItem("Move to Trash");
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        deleteItem.setOnAction(e -> moveToTrash());
        Menu editMenu = new Menu("_Edit", null, pinItem, archiveItem, new SeparatorMenuItem(), deleteItem);

        MenuItem allNotes = new MenuItem("All Notes");
        allNotes.setOnAction(e -> switchView(View.ALL));
        MenuItem pinned = new MenuItem("Pinned");
        pinned.setOnAction(e -> switchView(View.PINNED));
        MenuItem archived = new MenuItem("Archived");
        archived.setOnAction(e -> switchView(View.ARCHIVE));
        MenuItem trash = new MenuItem("Trash");
        trash.setOnAction(e -> switchView(View.TRASH));
        MenuItem emptyTrash = new MenuItem("Empty Trash...");
        emptyTrash.setOnAction(e -> emptyTrash());
        Menu viewMenu = new Menu("_View", null, allNotes, pinned, archived, trash, emptyTrash);

        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> Dialogs.info(
                "NoteApp 1.0 - an offline note-taking desktop app.\n"
                        + "JavaFX + SQLite. All data stays on this computer."));
        Menu helpMenu = new Menu("_Help", null, about);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, viewMenu, helpMenu);
        menuBar.setUseSystemMenuBar(false);
        return menuBar;
    }

    private Node buildSidebar() {
        sidebarCategoriesBox.setPadding(new Insets(2, 0, 2, 12));
        sidebarTagsBox.setPadding(new Insets(2, 0, 2, 12));

        Button newCategoryButton = new Button("+ Category");
        newCategoryButton.setOnAction(e -> createCategory());

        Button newNoteButton = new Button("+ New Note");
        newNoteButton.getStyleClass().add("accent");
        newNoteButton.setOnAction(e -> createNewNote());

        VBox sidebar = new VBox(8,
                sectionLabel("VIEWS"),
                sidebarLink("All Notes", () -> switchView(View.ALL)),
                sidebarLink("Pinned", () -> switchView(View.PINNED)),
                sidebarLink("Archived", () -> switchView(View.ARCHIVE)),
                sidebarLink("Trash", () -> switchView(View.TRASH)),
                new javafx.scene.control.Separator(),
                sectionLabel("CATEGORIES"),
                sidebarCategoriesBox,
                newCategoryButton,
                new javafx.scene.control.Separator(),
                sectionLabel("TAGS"),
                sidebarTagsBox,
                new javafx.scene.control.Separator(),
                newNoteButton);
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(215);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-header");
        return label;
    }

    private Label sidebarLink(String text, Runnable action) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-link");
        label.setOnMouseClicked(e -> action.run());
        return label;
    }

    private Node buildCenter() {
        noteListView = new ListView<>(notes);
        noteListView.setCellFactory(view -> new NoteListCell());
        noteListView.getSelectionModel().selectedItemProperty().addListener((obs, oldNote, newNote) ->
                openNote(newNote));

        VBox listPane = new VBox(noteListView);
        listPane.setPadding(new Insets(8));
        VBox.setVgrow(noteListView, Priority.ALWAYS);

        editorContainer = new VBox(editor.getRoot());
        editorPlaceholder = new Label("Select a note or press Ctrl+N to create one.");
        editorPlaceholder.getStyleClass().add("placeholder");
        editorContainer.setVisible(false);
        editorPlaceholder.setVisible(true);

        StackPane center = new StackPane(editorPlaceholder, editorContainer);
        return center;
    }

    // ================================================================== note list cells

    private final class NoteListCell extends ListCell<Note> {
        @Override
        protected void updateItem(Note note, boolean empty) {
            super.updateItem(note, empty);
            if (empty || note == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            setText(null);
            setGraphic(renderRow(note));
            setContextMenu(buildMenu(note));
            getStyleClass().removeAll("pinned-cell");
            if (note.isPinned()) {
                getStyleClass().add("pinned-cell");
            }
        }

        private Node renderRow(Note note) {
            Label title = new Label((note.isPinned() ? "\u2605 " : "") + note.getTitle());
            title.getStyleClass().add("note-title");
            Label meta = new Label(metaLine(note));
            meta.getStyleClass().add("note-meta");
            return new VBox(2, title, meta);
        }

        private String metaLine(Note note) {
            StringBuilder sb = new StringBuilder("Updated: ")
                    .append(com.example.noteapp.util.DateUtil.toRelativeUi(note.getUpdatedAt()));
            if (!note.getTags().isEmpty()) {
                sb.append("    ");
                note.getTags().forEach(tag -> sb.append('#').append(tag).append(' '));
            }
            return sb.toString();
        }

        private ContextMenu buildMenu(Note note) {
            if (currentView == View.TRASH) {
                MenuItem restore = new MenuItem("Restore");
                restore.setOnAction(e -> { selectedNote = note; restoreFromTrash(); });
                MenuItem delete = new MenuItem("Delete Permanently");
                delete.setOnAction(e -> { selectedNote = note; deletePermanently(); });
                return new ContextMenu(restore, delete);
            }
            MenuItem pin = new MenuItem(note.isPinned() ? "Unpin" : "Pin");
            pin.setOnAction(e -> { selectedNote = note; togglePin(); });
            MenuItem archive = new MenuItem(note.isArchived() ? "Unarchive" : "Archive");
            archive.setOnAction(e -> { selectedNote = note; toggleArchive(); });
            MenuItem trash = new MenuItem("Move to Trash");
            trash.setOnAction(e -> { selectedNote = note; moveToTrash(); });
            return new ContextMenu(pin, archive, trash);
        }
    }

    // ================================================================== note actions

    private void createNewNote() {
        TextInputDialog titleDialog = new TextInputDialog();
        titleDialog.setTitle("New Note");
        titleDialog.setHeaderText("Create a new note");
        titleDialog.setContentText("Title:");
        Optional<String> result = titleDialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        try {
            Long categoryId = currentView == View.CATEGORY ? selectedCategoryId : defaultCategoryIdForNewNotes();
            Note note = noteService.createNote(result.get(), "", categoryId, java.util.Set.of());
            refreshNotes();
            refreshSidebar();
            selectNote(note);
        } catch (InvalidNoteException e) {
            Dialogs.warning(e.getMessage());
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private Long defaultCategoryIdForNewNotes() {
        try {
            String name = settingsService.getSettings().getDefaultCategory();
            if (!name.isBlank()) {
                return categoryService.findByName(name).map(Category::getId).orElse(null);
            }
        } catch (Exception e) {
            LOG.debug("Default category could not be resolved", e);
        }
        return null;
    }

    private void openNote(Note note) {
        selectedNote = note;
        if (note == null) {
            editorContainer.setVisible(false);
            editorPlaceholder.setVisible(true);
            return;
        }
        editor.open(note, unused -> refreshNotes(), this::showSaveFailure);
        editorContainer.setVisible(true);
        editorPlaceholder.setVisible(false);
    }

    private void showSaveFailure(String message) {
        Dialogs.warning(message);
    }

    private void togglePin() {
        if (selectedNote == null) return;
        try {
            noteService.setPinned(selectedNote.getId(), !selectedNote.isPinned());
            refreshNotes();
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private void toggleArchive() {
        if (selectedNote == null) return;
        try {
            noteService.setArchived(selectedNote.getId(), !selectedNote.isArchived());
            refreshNotes();
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private void moveToTrash() {
        if (selectedNote == null) return;
        try {
            noteService.moveToTrash(selectedNote.getId());
            selectedNote = null;
            editor.close();
            refreshNotes();
            refreshSidebar();
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private void restoreFromTrash() {
        if (selectedNote == null) return;
        try {
            noteService.restoreFromTrash(selectedNote.getId());
            refreshNotes();
            refreshSidebar();
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private void deletePermanently() {
        if (selectedNote == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete \"" + selectedNote.getTitle() + "\"? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete permanently");
        confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(bt -> {
            try {
                noteService.deletePermanently(selectedNote.getId());
                selectedNote = null;
                editor.close();
                refreshNotes();
                refreshSidebar();
            } catch (DatabaseException e) {
                Dialogs.databaseError(e);
            }
        });
    }

    private void emptyTrash() {
        try {
            long count = noteService.trashCount();
            if (count == 0) {
                Dialogs.info("The trash is already empty.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    count + " note(s) will be permanently deleted. This cannot be undone.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText("Empty trash");
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(bt -> {
                try {
                    noteService.emptyTrash();
                    selectedNote = null;
                    refreshNotes();
                    refreshSidebar();
                } catch (DatabaseException e) {
                    Dialogs.databaseError(e);
                }
            });
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    // ================================================================== views & refresh

    private void switchView(View view) {
        currentView = view;
        refreshNotes();
    }

    private void refreshNotes() {
        try {
            notes.setAll(loadCurrentView());
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private List<Note> loadCurrentView() throws DatabaseException {
        String text = searchField == null ? "" : searchField.getText();
        boolean hasText = !text.isBlank();
        com.example.noteapp.repository.SortOrder sort = com.example.noteapp.repository.SortOrder
                .values()[sortBox == null ? 0 : Math.max(0, sortBox.getSelectionModel().getSelectedIndex())
                % com.example.noteapp.repository.SortOrder.values().length];
        return switch (currentView) {
            case ALL -> hasText ? searchService.search(text, sort) : noteService.listActiveNotes();
            case PINNED -> noteService.listPinned();
            case ARCHIVE -> noteService.listArchive();
            case TRASH -> hasText ? searchService.searchTrash(text, sort) : noteService.listTrash();
            case CATEGORY -> noteService.listByCategory(selectedCategoryId, sort);
            case TAG -> noteService.listByTag(selectedTag, sort);
        };
    }

    private void selectNote(Note note) {
        noteListView.getSelectionModel().select(note);
        noteListView.scrollTo(note);
    }

    private void refreshSidebar() {
        try {
            sidebarCategoriesBox.getChildren().clear();
            for (Category category : categoryService.list()) {
                Label link = sidebarLink(
                        category.getName() + "  (" + categoryService.countNotesIn(category.getId()) + ")",
                        () -> {
                            selectedCategoryId = category.getId();
                            currentView = View.CATEGORY;
                            refreshNotes();
                        });
                link.setContextMenu(categoryMenu(category));
                sidebarCategoriesBox.getChildren().add(link);
            }
            if (sidebarCategoriesBox.getChildren().isEmpty()) {
                sidebarCategoriesBox.getChildren().add(mutedLabel("(none yet)"));
            }

            sidebarTagsBox.getChildren().clear();
            for (String tag : tagService.listTagNames()) {
                sidebarTagsBox.getChildren().add(sidebarLink("#" + tag, () -> {
                    selectedTag = tag;
                    currentView = View.TAG;
                    refreshNotes();
                }));
            }
            if (sidebarTagsBox.getChildren().isEmpty()) {
                sidebarTagsBox.getChildren().add(mutedLabel("(none yet)"));
            }
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-muted");
        return label;
    }

    private javafx.scene.control.ContextMenu categoryMenu(Category category) {
        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(e -> renameCategory(category));
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> deleteCategory(category));
        return new javafx.scene.control.ContextMenu(rename, delete);
    }

    private void createCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Category");
        dialog.setHeaderText("Create a category");
        dialog.setContentText("Name:");
        Optional<String> name = dialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) return;
        try {
            categoryService.create(name.get());
            refreshSidebar();
        } catch (com.example.noteapp.exception.DuplicateCategoryException | InvalidNoteException e) {
            Dialogs.warning(e.getMessage());
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private void renameCategory(Category category) {
        TextInputDialog dialog = new TextInputDialog(category.getName());
        dialog.setTitle("Rename Category");
        dialog.setHeaderText("Rename \"" + category.getName() + "\"");
        dialog.setContentText("New name:");
        Optional<String> name = dialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) return;
        try {
            categoryService.rename(category.getId(), name.get());
            refreshSidebar();
            refreshNotes();
        } catch (com.example.noteapp.exception.DuplicateCategoryException | InvalidNoteException e) {
            Dialogs.warning(e.getMessage());
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    private void deleteCategory(Category category) {
        try {
            long inUse = categoryService.countNotesIn(category.getId());
            String message = inUse > 0
                    ? inUse + " note(s) use this category and will become uncategorized. Delete?"
                    : "Delete category \"" + category.getName() + "\"?";
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText("Delete category");
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(bt -> {
                try {
                    categoryService.delete(category.getId());
                    if (currentView == View.CATEGORY && category.getId() == selectedCategoryId) {
                        currentView = View.ALL;
                    }
                    refreshSidebar();
                    refreshNotes();
                } catch (DatabaseException e) {
                    Dialogs.databaseError(e);
                }
            });
        } catch (com.example.noteapp.exception.AppException e) {
            Dialogs.databaseError(e);
        }
    }

    // ================================================================== import / export / backup / settings

    private void exportNotes() {
        java.io.File file = Dialogs.saveFile(stage, "Export notes", "noteapp-export",
                exportService.availableFormats());
        if (file == null) return;
        try {
            String format = com.example.noteapp.util.FileUtil.extensionOf(file.getName()).toUpperCase();
            exportService.exportAllNotes(format, file.toPath());
            Dialogs.info("Exported successfully to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            Dialogs.error("Export failed", e);
        }
    }

    private void importNotes() {
        java.io.File file = Dialogs.openFile(stage, "Import notes", "json");
        if (file == null) return;
        try {
            int count = importService.importNotes(file.toPath());
            refreshNotes();
            refreshSidebar();
            Dialogs.info(count + " note(s) imported.");
        } catch (com.example.noteapp.exception.ImportException e) {
            Dialogs.error("Import failed", e);
        }
    }

    private void doBackup() {
        Path folder = Dialogs.chooseDirectory(stage, "Choose backup folder",
                settingsService.defaultBackupLocation());
        if (folder == null) return;
        try {
            Path created = backupService.backup(folder);
            Dialogs.info("Backup created:\n" + created);
        } catch (com.example.noteapp.exception.BackupException e) {
            Dialogs.error("Backup failed", e);
        }
    }

    private void doRestore() {
        if (!Dialogs.confirm("Restore backup",
                "Restoring will replace ALL current notes and settings.\n"
                        + "A safety copy of your current data will be kept. Continue?")) {
            return;
        }
        Path folder = Dialogs.chooseDirectory(stage, "Choose the backup folder",
                settingsService.defaultBackupLocation());
        if (folder == null) return;
        if (!backupService.isValidBackup(folder)) {
            Dialogs.warning("That folder is not a NoteApp backup (noteapp.db is missing).");
            return;
        }
        try {
            databaseManager.close();
            Path safety = backupService.restore(folder);
            Dialogs.info("Restore complete.\nA safety copy of your previous data is at:\n" + safety
                    + "\n\nPlease start NoteApp again.");
            Platform.exit();
        } catch (Exception e) {
            Dialogs.error("Restore failed", e);
            LOG.info("Restore failed; the previous data is unchanged. Restart NoteApp to continue.");
            Platform.exit();
        }
    }

    private void showSettings() {
        new SettingsView(settingsService, categoryService, this::applyTheme).showAndWait(stage);
    }

    private void applyTheme() {
        if (scene == null) return;
        AppSettings.Theme theme = settingsService.getSettings().getTheme();
        var stylesheet = getClass().getResource("/css/" + (theme == AppSettings.Theme.DARK ? "dark" : "light") + ".css");
        scene.getStylesheets().clear();
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    // ================================================================== shutdown guard

    /** Asks about unsaved editor changes; true when it is safe to proceed. */
    private boolean confirmUnsavedChanges() {
        if (!editor.hasUnsavedChanges()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText("Unsaved changes");
        alert.setContentText("This note has unsaved changes. Save before closing?");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            return false;
        }
        if (choice.get() == save) {
            return editor.saveNow();
        }
        return true; // discard
    }
}
