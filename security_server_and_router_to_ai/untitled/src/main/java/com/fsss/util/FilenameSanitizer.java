package com.fsss.util;

public final class FilenameSanitizer {
    private FilenameSanitizer() {
    }

    public static String sanitize(String filename) {
        if (filename == null) {
            return "file";
        }
        String trimmed = filename.replace("\\", "/");
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash >= 0) {
            trimmed = trimmed.substring(lastSlash + 1);
        }
        trimmed = trimmed.replaceAll("[\\r\\n\\t\\u0000]", "_");
        trimmed = trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (trimmed.isBlank()) {
            trimmed = "file";
        }
        if (trimmed.length() > 128) {
            trimmed = trimmed.substring(0, 128);
        }
        return trimmed;
    }

    public static boolean isSuspicious(String filename) {
        if (filename == null) {
            return true;
        }
        String lower = filename.toLowerCase();
        return lower.contains("..")
                || lower.contains("/.")
                || lower.contains("\\")
                || lower.contains("%00")
                || lower.contains("\\0")
                || lower.contains("\u0000");
    }
}
