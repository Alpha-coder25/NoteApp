package com.example.noteapp.model;

/**
 * Typed view over the key/value {@code settings} table. Keys are constants;
 * only {@link SettingKey#PIN_HASH} stores security-sensitive data (a salted
 * hash - never the PIN itself).
 */
public class AppSettings {

    /** Well-known setting keys persisted in the database. */
    public static final class SettingKey {
        public static final String THEME = "theme";
        public static final String AUTOSAVE_DELAY_SECONDS = "autosave_delay_seconds";
        public static final String DEFAULT_CATEGORY = "default_category";
        public static final String BACKUP_LOCATION = "backup_location";
        public static final String EXPORT_FORMAT = "export_format";
        public static final String PIN_HASH = "pin_hash";
        public static final String PIN_SALT = "pin_salt";

        private SettingKey() { }
    }

    /** Supported UI themes. */
    public enum Theme { LIGHT, DARK }

    private Theme theme = Theme.LIGHT;
    private int autosaveDelaySeconds = 2;
    private String defaultCategory = "";
    private String backupLocation = "";
    private String exportFormat = "JSON";
    private String pinHash = null;
    private String pinSalt = null;

    public Theme getTheme() { return theme; }

    public void setTheme(Theme theme) {
        this.theme = theme == null ? Theme.LIGHT : theme;
    }

    /** Auto-save delay in seconds, clamped to a sane 1..30 range. */
    public int getAutosaveDelaySeconds() { return autosaveDelaySeconds; }

    public void setAutosaveDelaySeconds(int seconds) {
        this.autosaveDelaySeconds = Math.max(1, Math.min(30, seconds));
    }

    public String getDefaultCategory() { return defaultCategory; }

    public void setDefaultCategory(String defaultCategory) {
        this.defaultCategory = defaultCategory == null ? "" : defaultCategory.strip();
    }

    public String getBackupLocation() { return backupLocation; }

    public void setBackupLocation(String backupLocation) {
        this.backupLocation = backupLocation == null ? "" : backupLocation.strip();
    }

    public String getExportFormat() { return exportFormat; }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = "TXT".equalsIgnoreCase(exportFormat) ? "TXT" : "JSON";
    }

    /** Base64 salted hash of the lock PIN, or null when the lock is disabled. */
    public String getPinHash() { return pinHash; }

    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getPinSalt() { return pinSalt; }

    public void setPinSalt(String pinSalt) { this.pinSalt = pinSalt; }

    /** True when the local application lock is enabled. */
    public boolean isLockEnabled() { return pinHash != null && !pinHash.isBlank(); }
}
