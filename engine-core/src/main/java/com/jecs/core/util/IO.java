package com.jecs.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * I/O utility class for file operations and console output.
 *
 * Provides convenient methods for reading files, printing to console,
 * and managing resource paths.
 */
public class IO {

    /**
     * Print message to console with newline.
     *
     * @param message message to print
     */
    public static void println(String message) {
        System.out.println(message);
    }

    /**
     * Print empty line to console.
     */
    public static void println() {
        System.out.println();
    }

    /**
     * Print message to console without newline.
     *
     * @param message message to print
     */
    public static void print(String message) {
        System.out.print(message);
    }

    /**
     * Print error message to stderr.
     *
     * @param message error message
     */
    public static void error(String message) {
        System.err.println("[ERROR] " + message);
    }

    /**
     * Print warning message.
     *
     * @param message warning message
     */
    public static void warn(String message) {
        System.out.println("[WARN] " + message);
    }

    /**
     * Print info message.
     *
     * @param message info message
     */
    public static void info(String message) {
        System.out.println("[INFO] " + message);
    }

    /**
     * Read entire file as string.
     *
     * @param filePath path to file
     * @return file contents as string
     * @throws IOException if file cannot be read
     */
    public static String readFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readString(path);
    }

    /**
     * Read entire file as byte array.
     *
     * @param filePath path to file
     * @return file contents as bytes
     * @throws IOException if file cannot be read
     */
    public static byte[] readFileBytes(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllBytes(path);
    }

    /**
     * Check if file exists.
     *
     * @param filePath path to check
     * @return true if file exists
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * Get resource path (for loading from resources folder).
     *
     * @param resourceName resource name (e.g., "shaders/sprite.vert.glsl")
     * @return full path to resource
     */
    public static String getResourcePath(String resourceName) {
        return "src/main/resources/" + resourceName;
    }
}
