# NoteApp — Offline Java Desktop Note-Taking Application

A lightweight, professional **offline** note-taking desktop application for Windows, built with **Java 17+ / JavaFX / SQLite** and designed as a complete **Object-Oriented Programming (OOP)** project: encapsulation, abstraction, inheritance, polymorphism, interfaces, composition, exception handling, validation, file handling and database operations are all demonstrated with *genuinely useful* (not artificial) code.

> 📖 **Just want to use the app?** Read the **[User Guide](docs/USER_GUIDE.md)** — a plain-language walkthrough of every feature, with screenshots-style layouts, backup/restore instructions and troubleshooting.

The application runs completely offline. There is no backend server, cloud service, REST API, online account or synchronization. All data stays on the user's machine.

---

## 1. Features

| Area | What you can do |
|---|---|
| Notes | Create, read, edit, delete (soft → trash), restore, permanently delete, empty trash |
| Organization | Categories (create / rename / delete with in-use warning), tags (many-to-many, search, filter) |
| Visibility | Pin important notes (visually marked, float to top), archive into a dedicated view |
| Search | Case-insensitive search across **title, content, category name and tags** — backed by indexed SQL |
| Sorting | Title A-Z / Z-A, created date newest/oldest, modified date newest/oldest |
| Auto-save | Debounced (not per keystroke) with a `Saving… / Saved` indicator; on failure the content is **never discarded** |
| Import / Export | Export **TXT** and **JSON**; import **JSON** (round-trips with JSON export); new formats plug in via one class |
| Backup | Full local backup and restore; restore validates the backup, keeps a safety copy and can roll back |
| Settings | Theme (light/dark), auto-save delay, default category, backup location, preferred export format — persisted in SQLite |
| Local lock | Optional 4–8 digit PIN lock; only a **salted SHA-256 hash** is stored, never the PIN |
| Shortcuts | `Ctrl+N` new · `Ctrl+S` save · `Ctrl+F` search · `Ctrl+Shift+P` pin · `Delete` trash · `Ctrl+Z/Y` undo/redo inside text fields |

Keyboard shortcuts use JavaFX accelerators and menu key bindings, so they never interfere with normal text editing.

## 2. Technologies

- **Java 17+** (built and tested on Java 21 LTS)
- **JavaFX 21** — desktop GUI
- **SQLite** via **JDBC** (`sqlite-jdbc` embedded driver) — local persistence
- **Maven** — build & dependency management
- **JUnit 5** — unit testing
- **SLF4J** (+ `slf4j-jdk14`) — logging
- **jpackage** — Windows packaging (`NoteApp.exe` with bundled runtime)

No other runtime dependencies. The JSON import/export uses a small built-in parser (`util/JsonUtil`) — no Gson/Jackson — keeping the app lightweight and fully offline.

## 3. Architecture

```
JavaFX UI            ui/        (MainWindow, NoteEditor, SettingsView, Dialogs)
    ↓  calls only
Service layer        service/   (NoteService, SearchService, CategoryService,
                                  TagService, ExportService, ImportService,
                                  BackupService, SettingsService)
    ↓  calls only
Repository layer     repository/ (Repository<T>, NoteRepository,
                                  CategoryRepository, TagRepository,
                                  SettingsRepository, DatabaseManager)
    ↓
SQLite database      %APPDATA%\NoteApp\noteapp.db
```

- **UI classes contain no SQL and no business rules.** They translate user actions into service calls and service failures into dialogs.
- **Services own business rules** (duplicate categories, trash lifecycle, PIN hashing, import validation).
- **Repositories own SQL.** Every statement is a `PreparedStatement`; every multi-step write runs in a transaction via `DatabaseManager.inTransaction(...)`.
- **Models are independent** of UI and JDBC, so they are trivially unit-testable.

### Package structure

```
src/main/java/com/example/noteapp/
├── Main.java               composition root + JavaFX entry point
├── Launcher.java           fat-jar entry shim (JavaFX packaging requirement)
├── model/                  Note, Category, Tag, AppSettings
├── repository/             DatabaseManager, Repository<T>, NoteRepository,
│                           CategoryRepository, TagRepository, SettingsRepository,
│                           NoteQuery, SortOrder
├── service/                NoteService, SearchService, CategoryService,
│                           TagService, ExportService, ImportService,
│                           BackupService, SettingsService
├── exception/              AppException + concrete failures (see §5)
├── export/                 Exporter interface, TextExporter, JsonExporter
├── ui/                     MainWindow, NoteEditor, SettingsView, Dialogs
└── util/                   Validator, DateUtil, FileUtil, JsonUtil, PinHasher, Searchable
```

### Data location

User-specific, never inside the installation directory:

1. `NOTEAPP_DATA_DIR` environment variable (tests / portable use)
2. `%APPDATA%\NoteApp` (Windows default)
3. `~/.noteapp` (fallback on other systems)

## 4. Database schema

```sql
categories (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE          -- case-insensitive uniqueness
)

notes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    title       TEXT NOT NULL,
    content     TEXT NOT NULL DEFAULT '',
    category_id INTEGER  REFERENCES categories(id) ON DELETE SET NULL,
    created_at  TEXT NOT NULL,        -- ISO-8601, sortable as text
    updated_at  TEXT NOT NULL,
    is_pinned   INTEGER NOT NULL DEFAULT 0,
    is_archived INTEGER NOT NULL DEFAULT 0,
    is_deleted  INTEGER NOT NULL DEFAULT 0     -- soft deletion (trash)
)

tags    (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)
note_tags (                                  -- many-to-many
    note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    tag_id  INTEGER NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
)

settings (key TEXT PRIMARY KEY, value TEXT)        -- theme, autosave, PIN hash, ...
```

Indexes: `notes(updated_at)`, `notes(is_deleted)`, `notes(is_archived)`, `notes(category_id)`, `note_tags(tag_id)`.

Data-safety measures:

- Transactions wrap every multi-step write (save note + sync tags; settings; imports).
- `PRAGMA foreign_keys = ON` and `journal_mode = WAL` (crash-safe commits).
- Restore creates a **safety backup first** and rolls back on failure — the live database is never overwritten blindly.
- Permanent deletion always requires confirmation.

## 5. Exception handling strategy

All recoverable failures are **checked exceptions** rooted at `AppException`, so the UI can catch one type and show a friendly dialog while technical details travel in the cause chain to the log.

```
AppException (abstract)
├── NoteException
│   ├── NoteNotFoundException          unknown/never-persisted note id
│   └── InvalidNoteException           empty/oversized title, bad tag, bad PIN
├── DatabaseException                  wraps SQLException — SQL never escapes the repo layer
├── DuplicateCategoryException         case-insensitive duplicate category
├── BackupException                    backup/restore failure (with rollback info)
├── ExportException                    write failure / unknown format
├── ImportException                    missing/invalid file, invalid JSON, validation failure
└── SettingsException                  invalid persisted settings / lock state
```

Where they are handled:

| Layer | Policy |
|---|---|
| Repository | Throws `DatabaseException` (wrapping SQL); never catches-and-ignores |
| Service | Validates first, throws the *specific* exception; translates lower-level errors |
| UI | Catches `AppException` once (`Dialogs`), shows friendly text, logs the cause |
| `Main` | Fatal start-up failures → error dialog + clean exit (no stack-trace window) |

The application never terminates because of a normal user mistake (empty title, duplicate category, missing file, wrong PIN, no search results…).

## 6. OOP concepts used

Short version — full discussion in [`docs/OOP_CONCEPTS.md`](docs/OOP_CONCEPTS.md), architecture details in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

| Concept | Demonstrated by |
|---|---|
| **Encapsulation** | `Note`, `Category`, `Tag`, `AppSettings`: private fields + validating setters; an invalid note *cannot exist* |
| **Abstraction** | `Repository<T>` and `Exporter` interfaces; `AppException` as an abstract root; `Searchable` contract |
| **Inheritance** | Exception hierarchy (`AppException` → …); `TextExporter`/`JsonExporter` are-a `Exporter` |
| **Polymorphism** | `ExportService` holds `Exporter` and picks the implementation at runtime; adding a format = one class, zero core changes (covered by a test) |
| **Composition** | `NoteService` composes repositories; `MainWindow` composes services and the `NoteEditor`; `Main` wires everything |
| **SOLID** | SRP (one class per concern), OCP (register new exporters), LSP (all exporters substitute), ISP (small focused interfaces), DIP (services depend on `Repository<T>`/`Exporter` abstractions) |

## 7. How to run

### Option A — with Maven (development)

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.example.noteapp.Launcher"
```

or simply open the project in IntelliJ IDEA / Eclipse / VS Code and run `Main`.

### Option B — the fat jar

```bash
mvn clean package -DskipTests
java -jar target/NoteApp.jar
```

(Requires a local Java 17+ runtime that includes JavaFX, or use the packaged build below.)

### Option C — packaged Windows app (recommended)

See §9: produces `NoteApp.exe` with a bundled Java runtime — **no Java installation needed for users**.

## 8. How to test

```bash
mvn test          # 62 unit tests: services, validation, search, trash lifecycle,
                  # export/import, backup, settings, PIN hashing, JSON utility
```

Tests run against a real (temporary) SQLite database isolated per test via JUnit's `@TempDir` — the same stack production uses, no mocks pretending SQLite behaves differently.

## 9. How to package for Windows

### Using the provided script

```bash
# Git Bash / MSYS
./package.sh
```

```bat
:: cmd / PowerShell (needs JAVA_HOME set)
package.bat
```

### Manually

```bash
mvn clean package -DskipTests
jpackage --type app-image --name NoteApp --app-version 1.0.0 \
  --input target --main-jar NoteApp.jar --dest target/dist
```

Result: **`target/dist/NoteApp/NoteApp.exe`** — a self-contained folder including a private Java runtime. Users double-click the exe; no Java installation, no internet, no admin rights required.

To produce a real `NoteApp-Setup.exe` installer, install [WiX Toolset 3.x](https://wixtoolset.org/) and add `--type exe --win-menu --win-shortcut --win-dir-chooser` to the jpackage command (see the commented section in `package.bat`).

## 10. Logging

SLF4J → `java.util.logging`. Logged: database initialization/closure, settings changes, backup/restore operations, import/export failures, unexpected exceptions. **Never logged:** PIN values, PIN hashes, note content. Technical detail lives in the log; user dialogs stay friendly.

## 11. Project layout

```
pom.xml              Maven build (javafx-controls, sqlite-jdbc, slf4j, junit)
package.bat/.sh      jpackage packaging scripts
build.sh             convenience wrapper for the portable toolchain in tools/
docs/                ARCHITECTURE.md, OOP_CONCEPTS.md (academic write-up)
src/                 main + test sources (see §3)
```

---

*NoteApp 1.0 — fully offline, fast, persistent, safe against common errors, maintainable, properly object-oriented, testable and easy to extend.*
