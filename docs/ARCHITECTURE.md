# Architecture

## 1. Layered design

```
┌──────────────────────────────────────────────────────────┐
│  UI layer        ui/                                     │
│  MainWindow · NoteEditor · SettingsView · Dialogs        │
│  Responsibility: widgets, layout, shortcuts, dialogs.    │
│  Knows NOTHING about SQL, JDBC or business rules.        │
└──────────────┬───────────────────────────────────────────┘
               │ calls services only
┌──────────────▼───────────────────────────────────────────┐
│  Service layer   service/                                │
│  NoteService · SearchService · CategoryService           │
│  TagService · ExportService · ImportService              │
│  BackupService · SettingsService                         │
│  Responsibility: business rules + use cases.             │
│  Validates input, enforces invariants, owns transactions │
│  boundaries (via DatabaseManager).                       │
└──────────────┬───────────────────────────────────────────┘
               │ calls repositories only
┌──────────────▼───────────────────────────────────────────┐
│  Repository layer  repository/                           │
│  Repository<T> · NoteRepository · CategoryRepository     │
│  TagRepository · SettingsRepository · DatabaseManager    │
│  Responsibility: SQL + row mapping. Every statement is a │
│  PreparedStatement; multi-step writes are transactional. │
└──────────────┬───────────────────────────────────────────┘
               │ JDBC
┌──────────────▼───────────────────────────────────────────┐
│  SQLite   %APPDATA%\NoteApp\noteapp.db (WAL mode, FKs on)│
└──────────────────────────────────────────────────────────┘
```

**Rule enforced throughout the codebase:** a layer may only call the layer directly beneath it. The UI never touches `Connection`, `PreparedStatement` or `ResultSet`; models never touch anything.

## 2. Composition root

`Main` (JavaFX `Application`) is the single place where objects are created and wired:

```
DatabaseManager
   ├── NoteRepository ────┐
   ├── CategoryRepository ├─→ Services ─→ MainWindow ─→ NoteEditor / SettingsView
   ├── TagRepository      │
   └── SettingsRepository ┘
```

No static/global state is shared. Everything flows through constructor parameters (explicit dependencies, easy to test).

## 3. Cross-cutting components

| Component | Role |
|---|---|
| `DatabaseManager` | Owns the single `Connection`, creates the schema idempotently on first launch, exposes `inTransaction(SqlWork)` for commit/rollback, resolves the per-user data directory (`%APPDATA%\NoteApp`, overridable via `NOTEAPP_DATA_DIR`) |
| `Validator` | Centralized input rules (titles, tags, categories, PIN format, integer settings) — one place, applied identically from UI, import and tests |
| `Dialogs` | Every user-facing alert/file-chooser; converts `AppException`s into friendly messages and logs the technical cause |
| `JsonUtil` | Dependency-free JSON reader/writer for import/export/backups |
| `PinHasher` | Salted SHA-256 for the optional local PIN lock |
| `DateUtil` | ISO-8601 storage format ↔ human "Today/Yesterday" UI format |

## 4. Request flows (examples)

### Save a note (auto-save debounce, 2 s after last keystroke)

```
keystroke → NoteEditor.scheduleAutosave()        (restarts a JavaFX Timeline)
          → saveNow()
          → Note.setTitle/setContent             (validation happens HERE, in the model)
          → NoteService.updateNote()             (existsById check + validate)
          → NoteRepository.save()                (UPDATE notes … + syncTags, in ONE transaction)
          → "Saved" indicator / friendly error, content kept on failure
```

### Search

```
search field keystroke → MainWindow.refreshNotes()
    → SearchService.search(text)
    → NoteRepository.findBy(NoteQuery)           (LIKE with ESCAPE, case-insensitive,
                                                  covers title+content+category+tags)
    → loadTags()                                  (single batched query, no N+1)
    → ListView update
```

### Restore a backup

```
MainWindow.doRestore()
    → confirm dialog (explicit user consent)
    → backupService.isValidBackup(folder)         (noteapp.db present?)
    → databaseManager.close()
    → BackupService.restore()
         1. safety copy of live data folder
         2. copy backup into staging folder
         3. atomic-ish folder swap
         4. on ANY failure: roll back to the safety copy
    → user informed, app restarts cleanly
```

## 5. Why these choices

- **Single connection, not a pool.** SQLite is file-local; one connection with WAL is the fastest safe default and eliminates connection lifecycle bugs.
- **Soft deletion in the model + query layer.** `is_deleted` filtering is part of every listing query, so the trash is just another view over the same rows — restore is one flag flip.
- **Query object (`NoteQuery`)** instead of dozens of repository methods: filters compose (category + text + sort) without SQL string duplication.
- **Tag sync inside the note-save transaction.** A note can never end up with half-written tags.
- **`NOTEAPP_DATA_DIR` override** makes the whole app testable and portable without touching code.
