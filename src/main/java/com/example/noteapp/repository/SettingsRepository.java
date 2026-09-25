package com.example.noteapp.repository;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.model.AppSettings;
import com.example.noteapp.model.AppSettings.SettingKey;
import com.example.noteapp.util.Validator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Loads and stores application settings in the key/value {@code settings} table. */
public class SettingsRepository {

    private final DatabaseManager database;

    public SettingsRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Reads all settings; unknown or malformed values fall back to defaults. */
    public AppSettings load() throws DatabaseException {
        Map<String, String> raw = loadRaw();
        AppSettings settings = new AppSettings();
        settings.setTheme(parseTheme(raw.get(SettingKey.THEME)));
        settings.setAutosaveDelaySeconds(
                Validator.parseIntSetting(raw.get(SettingKey.AUTOSAVE_DELAY_SECONDS), 2));
        settings.setDefaultCategory(raw.getOrDefault(SettingKey.DEFAULT_CATEGORY, ""));
        settings.setBackupLocation(raw.getOrDefault(SettingKey.BACKUP_LOCATION, ""));
        settings.setExportFormat(raw.getOrDefault(SettingKey.EXPORT_FORMAT, "JSON"));
        settings.setPinHash(raw.get(SettingKey.PIN_HASH));
        settings.setPinSalt(raw.get(SettingKey.PIN_SALT));
        return settings;
    }

    /** Writes every setting in one transaction. */
    public void save(AppSettings settings) throws DatabaseException {
        Map<String, String> raw = new HashMap<>();
        raw.put(SettingKey.THEME, settings.getTheme().name());
        raw.put(SettingKey.AUTOSAVE_DELAY_SECONDS, String.valueOf(settings.getAutosaveDelaySeconds()));
        raw.put(SettingKey.DEFAULT_CATEGORY, settings.getDefaultCategory());
        raw.put(SettingKey.BACKUP_LOCATION, settings.getBackupLocation());
        raw.put(SettingKey.EXPORT_FORMAT, settings.getExportFormat());
        if (settings.getPinHash() != null) {
            raw.put(SettingKey.PIN_HASH, settings.getPinHash());
        }
        if (settings.getPinSalt() != null) {
            raw.put(SettingKey.PIN_SALT, settings.getPinSalt());
        }
        database.inTransaction(conn -> {
            try (PreparedStatement upsert = conn.prepareStatement(
                    "INSERT INTO settings (key, value) VALUES (?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    upsert.setString(1, entry.getKey());
                    upsert.setString(2, entry.getValue());
                    upsert.executeUpdate();
                }
                return null;
            }
        });
    }

    /** Only the PIN columns (used when enabling/disabling the lock). */
    public void savePin(String hash, String salt) throws DatabaseException {
        database.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO settings (key, value) VALUES (?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                if (hash == null) {
                    ps.setString(1, SettingKey.PIN_HASH);
                    ps.setString(2, null);
                    ps.executeUpdate();
                    ps.setString(1, SettingKey.PIN_SALT);
                    ps.setString(2, null);
                    ps.executeUpdate();
                } else {
                    ps.setString(1, SettingKey.PIN_HASH);
                    ps.setString(2, hash);
                    ps.executeUpdate();
                    ps.setString(1, SettingKey.PIN_SALT);
                    ps.setString(2, salt);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    private Map<String, String> loadRaw() throws DatabaseException {
        String sql = "SELECT key, value FROM settings";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<String, String> map = new HashMap<>();
            while (rs.next()) {
                String value = rs.getString(2);
                map.put(rs.getString(1), value == null ? "" : value);
            }
            return map;
        } catch (SQLException e) {
            throw new DatabaseException("Unable to read application settings.", e);
        }
    }

    private static AppSettings.Theme parseTheme(String value) {
        if ("DARK".equalsIgnoreCase(value)) {
            return AppSettings.Theme.DARK;
        }
        return AppSettings.Theme.LIGHT;
    }
}
