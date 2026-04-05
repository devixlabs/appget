package dev.appget.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CodeGenUtils {

    private CodeGenUtils() {
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Uses {@link Files#delete} (not {@link java.io.File#delete}) so that
     * failures are surfaced as exceptions rather than silently ignored.
     */
    public static void deleteDirectory(final Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** Returns {@code str} with its first character lowered to lowercase. */
    public static String lowerFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /** Returns {@code str} with its first character upper-cased. */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /** Finds the index of the closing parenthesis that matches the open paren at {@code openParen}, or {@code -1}. */
    public static int findMatchingParen(String sql, int openParen) {
        int depth = 0;
        for (int i = openParen; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Splits {@code text} on {@code delimiter}, respecting parenthesized and bracketed groups. */
    public static List<String> smartSplit(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '(' || c == '[') {
                parenDepth++;
                current.append(c);
            } else if (c == ')' || c == ']') {
                parenDepth--;
                current.append(c);
            } else if (c == delimiter && parenDepth == 0) {
                String part = current.toString().trim();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String lastPart = current.toString().trim();
        if (!lastPart.isEmpty()) {
            parts.add(lastPart);
        }

        return parts;
    }

    /** Escapes backslashes, double quotes, and common whitespace characters for use in generated string literals. */
    public static String escapeString(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
