package com.example.noteapp.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formats timestamps for the UI and parses persisted ISO-8601 values. */
public final class DateUtil {

    private static final DateTimeFormatter UI_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, uuuu  h:mm a").withZone(ZoneId.systemDefault());

    private DateUtil() { }

    /** Machine-readable format used inside the database and JSON files. */
    public static String toStorage(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    public static Instant fromStorage(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /** Human-friendly format for list cells, e.g. "Sep 25, 2026  8:59 PM". */
    public static String toUi(Instant instant) {
        if (instant == null) {
            return "";
        }
        return UI_FORMAT.format(instant);
    }

    /** "Today", "Yesterday" or a plain date - used in the note list. */
    public static String toRelativeUi(Instant instant) {
        if (instant == null) {
            return "";
        }
        LocalDate date = LocalDate.ofInstant(instant, ZoneId.systemDefault());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (date.equals(today)) {
            return "Today " + DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(instant);
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return DateTimeFormatter.ofPattern("MMM d, uuuu").withZone(ZoneId.systemDefault()).format(instant);
    }
}
