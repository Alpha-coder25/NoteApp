package com.example.noteapp.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Small file-system helpers shared by backup, restore and export features. */
public final class FileUtil {

    private FileUtil() { }

    public static String readAll(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Lower-cased extension without the dot; empty string when the name has none. */
    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    public static void writeAll(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /** Recursively copies a directory tree (used to back up the data folder). */
    public static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path dest = target.resolve(source.relativize(src).toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Recursively deletes a directory; the database is closed first by the caller. */
    public static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOExceptionLite(e);
                }
            });
        }
    }

    /** Non-public helper: converts delete failures into a checked IOException. */
    private static final class UncheckedIOExceptionLite extends RuntimeException {
        UncheckedIOExceptionLite(IOException cause) {
            super(cause);
        }
    }
}
