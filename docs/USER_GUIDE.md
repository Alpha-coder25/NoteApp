# NoteApp — User Guide

A friendly, step-by-step guide to using NoteApp. No programming knowledge needed.

> **NoteApp is completely offline.** Everything you write stays on your own computer. There is no account, no internet connection and no cloud — your notes cannot leak anywhere.

---

## 1. Starting the app

| If you have… | Do this |
|---|---|
| **NoteApp.exe** (packaged version) | Double-click it. Nothing else to install. |
| **NoteApp.jar** | Double-click it, or run `java -jar NoteApp.jar` (needs Java 17+ with JavaFX). |
| **From source code** | Run `mvn compile exec:java -Dexec.mainClass="com.example.noteapp.Launcher"` — see the [main README](README.md) for full developer instructions. |

On first start NoteApp creates its database automatically at `%APPDATA%\NoteApp\noteapp.db`. You never need to touch this file yourself.

### If a PIN lock is enabled

You will be asked for your 4–8 digit PIN before the window opens. You get 5 attempts. Forgot the PIN? See [Troubleshooting](#10-troubleshooting).

---

## 2. The main window

```
+--------------------------------------------------------------+
| Menus   [Search..............]  [Sort: Last modified ▼]  ⚙   |
+-------------+------------------------------------------------+
| All Notes   |   ★ Pinned note title                          |
| Pinned      |     Updated: Today 9:14 PM  #java              |
| Archived    |                                                |
| Trash       |   Grocery list                                 |
|             |     Updated: Yesterday                         |
| CATEGORIES  |                                                |
|  Study (3)  |   ------------------------------------------   |
|  Work (7)   |   (editor opens here when you select a note)   |
|  + Category |                                                |
|-------------|                                                |
| TAGS        |                                                |
|  #java      |                                                |
|  #urgent    |                                                |
|-------------|                                                |
| + New Note  |                                                |
+-------------+------------------------------------------------+
```

- **Left sidebar** — switch between views, and click a category or tag to see only those notes. The numbers next to categories are note counts.
- **Middle list** — your notes. Pinned notes show a ★ and float to the top.
- **Right editor** — title, category, tags and content of the selected note.

## 3. Creating and editing notes

**Create:** press **Ctrl+N**, or click **+ New Note**, or *File → New Note*. Type a title and press OK. A title is required (up to 200 characters); content is optional but recommended.

**Edit:** click a note in the list and type in the editor. That's it — **auto-save** handles the rest:

- The status label shows **Saving…** while you type and flips to **Saved** a couple of seconds after you stop (the delay is configurable in Settings).
- Prefer to save yourself? Press **Ctrl+S**.
- If a save ever fails (e.g. a disk problem), you'll see *"Unable to save changes. Your current content has not been discarded."* — your text stays in the editor, so just try again. **NoteApp never silently loses your writing.**

**Delete (safely):** press the **✕ Delete** button, right-click the note → *Move to Trash*, or select it and press **Delete**. Deleted notes go to the **Trash** — see section 5.

### Tags

In the editor, type a word in the *"Add tag and press Enter"* field and press **Enter**. Tags are lower-case, up to 30 characters, and one note can have many. Remove a tag by clicking the ✕ on its chip. Click **#tag** in the sidebar to see every note with that tag.

### Categories

1. Click **+ Category** in the sidebar and give it a name (e.g. *Study*, *Work*).
2. Open a note and pick the category from the dropdown above the content.
3. Click the category in the sidebar to filter your notes.

Right-click a category in the sidebar to **rename** or **delete** it. Deleting a category never deletes notes — they simply become uncategorized (NoteApp warns you first if notes are using it). Duplicate names are not allowed.

## 4. Pinning and archiving

**Pin** — for notes you keep coming back to:
- Open the note and click **Pin** in the editor, or
- Right-click it in the list → *Pin*, or
- Select it and press **Ctrl+Shift+P**.

Pinned notes get a ★ and always sit at the top of the list. Click **Unpin** the same way when you're done.

**Archive** — for notes you want out of sight but not deleted (old projects, finished semesters…):
- Click **Archive** in the editor or right-click → *Archive*.

Archived notes disappear from *All Notes* and live in the **Archived** view in the sidebar. Unarchive from there anytime.

## 5. Trash: delete, restore, empty

Deleting is always two steps — nothing vanishes immediately:

```
Normal note ──delete──▶ Trash ──restore──▶ back to normal
                 │
                 └── delete permanently / empty trash ──▶ gone forever
```

- **Restore:** open the **Trash** view in the sidebar, right-click the note → *Restore*.
- **Delete permanently:** in the Trash, right-click → *Delete Permanently* (asks for confirmation).
- **Empty the whole trash:** *View → Empty Trash…* (asks for confirmation and shows how many notes will be gone).

While a note is in the trash it doesn't appear in searches or the normal list.

## 6. Searching, filtering and sorting

**Search** — click the search box (or press **Ctrl+F**) and type. The search covers:
- note **titles**
- note **content**
- **tags** (`urgent` finds every note tagged *urgent*)
- **category names** (`work` finds every note in the *Work* category)

Search is case-insensitive and starts as you type. No results? You'll simply see an empty list — nothing breaks.

**Filter** — combine any of these with a search:
- click a **category** or **tag** in the sidebar,
- switch to the *Pinned*, *Archived* or *Trash* view.

**Sort** — use the dropdown at the top right: *Last modified first*, *Oldest modified first*, *Newest first*, *Oldest first*, *Title A–Z*, *Title Z–A*.

## 7. Export and import

**Export** — *File → Export…*
1. Choose a format: **JSON** (structured, can be re-imported) or **TXT** (plain readable text).
2. Pick a folder and file name. Done.

Use JSON for backups/sharing between NoteApp installs; use TXT when you want to read or print notes in any program.

**Import** — *File → Import…*
1. Choose a **JSON** file (e.g. one you exported earlier).
2. NoteApp checks the file before touching your data — if anything is wrong you get a clear message and **nothing is changed**.
3. Categories mentioned in the file are created automatically if missing.

Importing the same file twice creates a second copy of those notes, so don't worry about overwriting anything.

## 8. Backup and restore

Your notes live in one folder, so backups are simple and complete.

**Backup** — *File → Backup…*
1. Choose a folder (a USB drive or a synced folder like OneDrive works great).
2. NoteApp creates a timestamped folder like `NoteApp-Backup_2026-09-25_21-30-00` containing your entire database.

**Restore** — *File → Restore…*
1. Read the warning: restoring replaces **all** current notes and settings.
2. Choose the backup folder you made earlier (it must contain `noteapp.db`).
3. NoteApp first saves a **safety copy** of your current data, then swaps the backup in. If anything fails mid-way, your old data is restored automatically.
4. Restart NoteApp when prompted.

If a restore ever goes wrong, the safety copy is next to your data folder (e.g. `%APPDATA%\NoteApp-Data_pre-restore_...`) — see [Troubleshooting](#10-troubleshooting).

## 9. Settings

Click the **⚙** button (top right):

| Setting | What it does |
|---|---|
| **Theme** | Light or Dark. Applies immediately and is remembered. |
| **Auto-save delay** | Seconds to wait after you stop typing before saving (1–30). Default: 2. |
| **Default category** | New notes get this category automatically. |
| **Backup folder** | Where *File → Backup…* starts by default. |
| **Export format** | Your preferred format, pre-selected in the export dialog. |
| **Application lock** | Optional **PIN (4–8 digits)** that is asked at every start. |

**About the PIN:** NoteApp stores only a salted hash of your PIN — never the PIN itself. To enable, set a new PIN; to disable or change it, enter the current PIN first. **If you forget the PIN there is no recovery** (by design — that's what makes the lock meaningful), but see Troubleshooting below for a last resort.

## 10. Troubleshooting

| Problem | What to do |
|---|---|
| **Forgot the PIN** | The lock is deliberately unrecoverable from inside the app. Last resort: close NoteApp, delete the `pin_hash` and `pin_salt` rows from the `settings` table in `%APPDATA%\NoteApp\noteapp.db` (any SQLite browser can do this), then restart. Your notes are untouched. |
| **"Unable to save changes"** | Check free disk space and that `%APPDATA%\NoteApp` is writable (e.g. not blocked by antivirus/backup software). Your text is still in the editor — nothing is lost. |
| **Restore failed** | Your previous data was rolled back automatically. Try again; if it keeps failing, copy the backup folder manually over `%APPDATA%\NoteApp` while NoteApp is closed. |
| **A note disappeared** | Check the **Trash** view and the **Archived** view — it's almost certainly in one of them. |
| **Window won't start** | Read the log at `%APPDATA%\NoteApp\logs` (or the console output if run from a terminal). The usual cause is a missing/broken Java install — use the packaged `NoteApp.exe`, which bundles Java. |
| **Everything else** | *File → Backup…* first, then reopen the app. NoteApp's database is crash-safe (WAL mode), so even a power cut mid-write cannot corrupt your notes. |

---

## Where is my data?

| What | Where |
|---|---|
| Database (all notes, categories, tags, settings) | `%APPDATA%\NoteApp\noteapp.db` |
| Backups (default location) | `%APPDATA%\NoteApp\backups\` |
| Safety copy made before a restore | `%APPDATA%\NoteApp-Data_pre-restore_<timestamp>\` |

To move NoteApp to a new computer: make a backup, install NoteApp there, and restore from the backup. That's it — everything travels with the backup file.

*For developers: build instructions, architecture and the OOP design are documented in the [main README](README.md) and the [docs/](docs/) folder.*
