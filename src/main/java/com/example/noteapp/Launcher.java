package com.example.noteapp;

/**
 * Entry point for the packaged jar. JavaFX refuses to start when the class in
 * the manifest's Main-Class attribute extends {@code javafx.application.Application}
 * but is not on the module path, so this tiny shim sits in front and simply
 * forwards to {@link Main}.
 */
public final class Launcher {

    private Launcher() { }

    public static void main(String[] args) {
        Main.main(args);
    }
}
