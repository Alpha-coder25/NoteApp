package com.example.noteapp.service;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.exception.SettingsException;
import com.example.noteapp.model.AppSettings;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.repository.SettingsRepository;
import com.example.noteapp.util.PinHasher;
import com.example.noteapp.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Loads, caches and persists {@link AppSettings}, and implements the optional
 * local application lock. Hashing is delegated to {@link PinHasher}.
 */
public class SettingsService {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository repository;
    private final DatabaseManager databaseManager;
    private AppSettings cached;

    public SettingsService(SettingsRepository repository, DatabaseManager databaseManager) throws SettingsException {
        this.repository = repository;
        this.databaseManager = databaseManager;
        try {
            this.cached = repository.load();
        } catch (DatabaseException e) {
            throw new SettingsException("Application settings could not be loaded.", e);
        }
    }

    /** Current settings (cached; always non-null). */
    public AppSettings getSettings() {
        return cached;
    }

    /** Validates and persists settings, then refreshes the cache. */
    public void saveSettings(AppSettings settings) throws SettingsException {
        if (settings.getAutosaveDelaySeconds() < 1 || settings.getAutosaveDelaySeconds() > 30) {
            throw new SettingsException("Auto-save delay must be between 1 and 30 seconds.");
        }
        try {
            repository.save(settings);
            cached = repository.load();
            LOG.info("Settings updated");
        } catch (DatabaseException e) {
            throw new SettingsException("Settings could not be saved.", e);
        }
    }

    // ------------------------------------------------------------------ application lock

    /** True when the local lock is enabled. */
    public boolean isLockEnabled() {
        return cached.isLockEnabled();
    }

    /** Default backup folder when the user has not chosen one: the data dir's "backups" subfolder. */
    public Path defaultBackupLocation() {
        String configured = cached.getBackupLocation();
        if (!configured.isBlank()) {
            return Path.of(configured);
        }
        return databaseManager.getDatabaseFile().getParent().resolve("backups");
    }

    /**
     * Enables the lock with the given PIN.
     *
     * @throws InvalidNoteException when the PIN is not 4-8 digits
     * @throws SettingsException when persisting fails
     */
    public void enableLock(String pin) throws InvalidNoteException, SettingsException {
        Validator.validatePin(pin);
        String salt = PinHasher.newSalt();
        try {
            repository.savePin(PinHasher.hash(pin, salt), salt);
            cached = repository.load();
            LOG.info("Application lock enabled");
        } catch (DatabaseException e) {
            throw new SettingsException("The lock could not be enabled.", e);
        }
    }

    /**
     * Verifies a PIN against the stored hash.
     *
     * @throws SettingsException when the lock state is inconsistent
     */
    public boolean verifyPin(String pin) throws SettingsException {
        if (!isLockEnabled()) {
            return true; // no lock - nothing to verify
        }
        if (pin == null || pin.isBlank()) {
            return false;
        }
        String salt = cached.getPinSalt();
        if (salt == null) {
            throw new SettingsException("Lock data is incomplete. Please disable and re-enable the lock.");
        }
        return MessageDigest.isEqual(
                PinHasher.hash(pin, salt).getBytes(StandardCharsets.UTF_8),
                cached.getPinHash().getBytes(StandardCharsets.UTF_8));
    }

    /** Disables the lock (the UI must have verified the current PIN first). */
    public void disableLock() throws SettingsException {
        try {
            repository.savePin(null, null);
            cached = repository.load();
            LOG.info("Application lock disabled");
        } catch (DatabaseException e) {
            throw new SettingsException("The lock could not be disabled.", e);
        }
    }
}
