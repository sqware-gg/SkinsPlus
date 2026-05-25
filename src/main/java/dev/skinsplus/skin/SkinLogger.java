package dev.skinsplus.skin;

public interface SkinLogger {
    void warning(String message);

    default void info(String message) {
    }

    default void fine(String message) {
    }
}
