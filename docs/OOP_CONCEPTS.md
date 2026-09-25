# OOP Concepts in NoteApp (Academic Documentation)

This document maps each required OOP concept to the concrete classes that demonstrate it. The concepts are used **because the design needs them**, not decorated on top.

---

## 1. Encapsulation

**Definition applied:** data and the operations that maintain its invariants live together; state is private and reachable only through behavior.

| Class | How |
|---|---|
| `model/Note` | All fields `private`. `setTitle()` rejects null/blank and strips over-length input; `setContent()` normalizes null → `""` and enforces the size cap; `addTag()` normalizes to lower-case and validates length. **An invalid `Note` cannot be constructed.** `getTags()` returns an unmodifiable view — callers must go through `addTag`/`removeTag` |
| `model/Category`, `model/Tag` | Same pattern: validating `setName()` with length caps |
| `model/AppSettings` | Clamps `autosaveDelaySeconds` into 1–30, normalizes export format; the PIN hash/salt fields are exposed only for the repository that persists them |
| `repository/DatabaseManager` | The `Connection` is private; the outside world can only execute work through `inTransaction(...)` — nobody can commit/rollback or bypass transaction handling |

Test evidence: `NoteValidationTest` proves an over-length title, blank title, mutated tag set and duplicate tag handling all behave correctly.

## 2. Abstraction

**Definition applied:** interfaces/abstract classes name a *responsibility*, hiding how it is fulfilled.

| Abstraction | Purpose |
|---|---|
| `repository/Repository<T>` | "Something that persists T." Services could be written against this contract; the concrete SQL lives behind it |
| `export/Exporter` | "Something that writes notes to a file in a format." `ExportService` and the UI depend on this idea, not on TXT or JSON classes |
| `util/Searchable` | "Something whose records can be filtered by free text." `SearchService` implements it; callers stay decoupled from `NoteRepository` details |
| `exception/AppException` (abstract) | Names the concept "a recoverable application failure" while never being thrown itself — a bare `AppException` is meaningless, so the class is abstract *by design* |
| `DatabaseManager.SqlWork<T>` | A functional abstraction over "a unit of transactional work" |

## 3. Inheritance

Only genuine *is-a* relationships:

```
AppException (abstract)
├── NoteException                  "is a" recoverable app failure in the note domain
│   ├── NoteNotFoundException      is a note-domain failure (lookup)
│   └── InvalidNoteException       is a note-domain failure (validation)
├── DatabaseException              wraps JDBC failures for the layers above
├── DuplicateCategoryException
├── BackupException
├── ExportException
├── ImportException
└── SettingsException
```

and:

- `TextExporter extends/implements` nothing but `Exporter` — it **is an** exporter, as is `JsonExporter`.
- `NoteRepository implements Repository<Note>` (likewise `CategoryRepository`, `TagRepository`).

`Main extends javafx.application.Application` is a framework-required inheritance. No artificial "Manager-of-Base" hierarchies exist; where composition fits better (services own repositories, `MainWindow` owns `NoteEditor`), composition is used.

## 4. Polymorphism (runtime)

**The showcase:** `ExportService` holds a `Map<String, Exporter>`. The UI asks for format `"JSON"` or `"TXT"`; the service dispatches to the right implementation **at runtime** through the `Exporter` reference — it never imports the concrete classes' types into its API.

```java
Exporter exporter = exporters.get(format.toUpperCase());  // dynamic dispatch
exporter.export(notes, target);
```

Proof by test: `ExportImportBackupTest.exporterPolymorphismAddsNewFormatWithoutCoreChanges` registers a brand-new "MARKDOWN" exporter at runtime and exports through the same code path with **zero changes** to `ExportService`.

Second example: `Repository<T>` references in service code could hold any repository implementation.

## 5. Composition

"Has-a" everywhere over inheritance:

| Owner | Composed parts |
|---|---|
| `NoteService` | `NoteRepository` |
| `CategoryService` | `CategoryRepository` + `NoteRepository` (for the in-use count) |
| `ExportService` | `NoteRepository` + `CategoryRepository` + a registry of `Exporter`s |
| `ImportService` | `NoteRepository` + `CategoryService` |
| `SettingsService` | `SettingsRepository` + `DatabaseManager` + `PinHasher` (utility) |
| `BackupService` | `DatabaseManager` |
| `MainWindow` | all services + `NoteEditor` + `Dialogs` |
| `NoteEditor` | `NoteService` + the `Note` it edits + a debounced `Timeline` |

The `Note` ↔ `Tag` relationship in the domain is also composition (a note owns its tag set), persisted via the `note_tags` join table.

## 6. Exception handling

Custom hierarchy (see `exception/`), **checked** on purpose: the compiler forces every caller to consciously handle recoverable failures.

| Exception | Thrown by | Handled in |
|---|---|---|
| `InvalidNoteException` | `Note` setters, `Validator` | `NoteService` (passes through), `NoteEditor.saveNow` → status label + dialog, import validation |
| `NoteNotFoundException` | `NoteService.getNote/updateNote/moveToTrash/restoreFromTrash` | UI catch-all `AppException` handler → dialog |
| `DatabaseException` | every repository method (wraps `SQLException`), `DatabaseManager` | services, then `Dialogs.databaseError` in the UI |
| `DuplicateCategoryException` | `CategoryService.create/rename` | `MainWindow.createCategory/renameCategory` → warning dialog |
| `BackupException` | `BackupService.backup/restore/deleteBackup` | `MainWindow.doBackup/doRestore` → error dialog, rollback info included |
| `ExportException` | `TextExporter/JsonExporter/ExportService` | `MainWindow.exportNotes` → dialog |
| `ImportException` | `ImportService` (file read, JSON parse, per-note validation, db write) | `MainWindow.importNotes` → dialog |
| `SettingsException` | `SettingsService` (load/save, PIN enable/verify/disable) | `SettingsView` → dialog; `Main` start-up → fatal dialog |

Rules followed throughout:

- No `catch (Exception e) { e.printStackTrace(); }` as a strategy — the only broad catches are in `Main` (fatal start-up) and `Dialogs.error` (last-resort user dialog + proper logging).
- Specific exceptions are caught specifically wherever behavior differs (e.g. `NoteEditor.saveNow` distinguishes validation failure from infrastructure failure).
- Technical detail (cause chains) reaches the log; users see friendly text.

## 7. SOLID — practical examples

| Principle | Where you can point at it |
|---|---|
| **S**ingle responsibility | `NoteRepository` does SQL; `NoteService` does rules; `Validator` validates; `PinHasher` hashes; `DateUtil` formats; each UI class owns one pane |
| **O**pen/closed | New export format = new `Exporter` class + `registerExporter(...)` call. `ExportService`, UI, models: untouched (test-proven) |
| **L**iskov substitution | Every `Exporter` implementation is substitutable in `ExportService`; every `Repository<T>` implementation satisfies the same contract; exception subtypes are substitutable for `AppException` in UI catch blocks |
| **I**nterface segregation | `Repository<T>` is 4 methods, `Exporter` is 3, `Searchable` is 1 — no fat interfaces forcing empty implementations |
| **D**ependency inversion | Services receive their collaborators through constructors and depend on `Repository`/`Exporter` abstractions; `Main` (the composition root) supplies concrete objects — high-level policy never imports low-level SQL |

## 8. Other fundamentals demonstrated

- **Validation** — centralized in `Validator` + validating model setters (UI, import and tests share the same rules).
- **File handling** — backup/restore tree copy, export writes, import reads with friendly `IOException` translation.
- **Database operations** — prepared statements only, transactions, FKs, indexes, soft deletion, WAL.
- **Logging** — SLF4J, never logging PINs or note content.
- **Testing** — 62 JUnit 5 tests, including the polymorphism proof, invalid-input cases and failure paths.
