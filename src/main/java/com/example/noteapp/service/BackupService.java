package com.example.noteapp.service;

import com.example.noteapp.exception.BackupException;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * File-based backup and restore of the whole data folder (database + settings).
 *
 * <p>Restore is deliberately defensive, per the data-safety requirements:
 * <ol>
 *   <li>validate the backup folder,</li>
 *   <li>copy the current data to a safety folder,</li>
 *   <li>swap the restored data in,</li>
 *   <li>roll back to the safety copy if the swap fails,</li>
 * </ol>
 * so the existing data is never overwritten blindly and a failed restore
 * cannot corrupt it.
 */
public class BackupService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final String DB_FILE = "noteapp.db";

    private final DatabaseManager databaseManager;

    public BackupService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /** True when the folder looks like a NoteApp backup. */
    public boolean isValidBackup(Path folder) {
        return folder != null && Files.isRegularFile(folder.resolve(DB_FILE));
    }

    /**
     * Copies the current data directory into {@code targetFolder}
     * (a timestamped subfolder is created automatically).
     *
     * @return the created backup folder
     */
    public Path backup(Path targetFolder) throws BackupException {
        Path source = databaseManager.getDatabaseFile().getParent();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path destination = targetFolder.resolve("NoteApp-Backup_" + stamp);
        try {
            Files.createDirectories(destination);
            // Copy only application data files (the SQLite db, -wal/-shm side
            // files and nothing else) - never recurse into user-chosen
            // subfolders, which may contain the backup itself (infinite loop).
            try (Stream<Path> files = Files.list(source)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (Files.isRegularFile(file)) {
                        Files.copy(file, destination.resolve(file.getFileName().toString()),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (!isValidBackup(destination)) {
                throw new BackupException("The backup copy is incomplete. Please try again.");
            }
            LOG.info("Backup created at {}", destination);
            return destination;
        } catch (IOException e) {
            throw new BackupException("The backup could not be created. Check the backup folder and try again.", e);
        }
    }

    /**
     * Restores a backup into the live data folder. The caller must close the
     * app (or at least the database) beforehand; this class reopens nothing.
     *
     * @return the safety-backup folder holding the replaced data (kept so the
     *         user can roll back manually)
     */
    public Path restore(Path backupFolder) throws BackupException {
        validateBackupFolder(backupFolder);

        Path live = databaseManager.getDatabaseFile().getParent();
        Path safety = live.resolveSibling("NoteApp-Data_pre-restore_" + timestamp());
        Path staging = live.resolveSibling("NoteApp-Data_restore-" + timestamp());

        try {
            // 1) safety copy of current data
            Files.createDirectories(safety);
            FileUtil.copyTree(live, safety);

            // 2) copy backup into staging, then swap folders
            Files.createDirectories(staging);
            FileUtil.copyTree(backupFolder, staging);
            Files.move(live, live.resolveSibling(live.getFileName() + "_old"));
            Files.move(staging, live);

            // 3) success: remove the swapped-out old data
            deleteTree(live.resolveSibling(live.getFileName() + "_old"));
            LOG.info("Backup restored from {}", backupFolder);
            return safety;
        } catch (IOException failure) {
            try {
                // roll back: put the original data back
                Path old = live.resolveSibling(live.getFileName() + "_old");
                if (Files.exists(old)) {
                    deleteTree(live);
                    Files.move(old, live);
                }
            } catch (IOException rollbackFailure) {
                throw new BackupException("Restore failed and could not be rolled back automatically. "
                        + "Your previous data is preserved in: " + safety, rollbackFailure);
            }
            throw new BackupException("The restore failed. Your previous data is unchanged.", failure);
        }
    }

    /** Deletes a backup folder after the user confirms in the UI. */
    public void deleteBackup(Path backupFolder) throws BackupException {
        try {
            deleteTree(backupFolder);
        } catch (IOException e) {
            throw new BackupException("The backup folder could not be deleted.", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void validateBackupFolder(Path folder) throws BackupException {
        if (!Files.isDirectory(folder)) {
            throw new BackupException("The selected backup folder is not valid.");
        }
        if (!isValidBackup(folder)) {
            throw new BackupException("This folder is not a NoteApp backup (noteapp.db is missing).");
        }
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
