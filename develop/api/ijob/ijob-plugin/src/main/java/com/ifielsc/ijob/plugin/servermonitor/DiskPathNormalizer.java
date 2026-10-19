package com.ifielsc.ijob.plugin.servermonitor;

import java.util.Locale;

/** Normalizes disk mount paths received from different operating systems. */
public final class DiskPathNormalizer {
    private DiskPathNormalizer() {}

    public static String normalize(String path) {
        if (path == null) {
            return null;
        }

        String value = collapseSeparators(path.trim().replace('\\', '/'));
        boolean windowsDrive =
                value.length() >= 2
                        && Character.isLetter(value.charAt(0))
                        && value.charAt(1) == ':';
        boolean uncPath = value.startsWith("//");

        if (windowsDrive && value.length() == 2) {
            value += "/";
        }
        while (value.length() > 1
                && value.endsWith("/")
                && !isWindowsDriveRoot(value)
                && !"//".equals(value)) {
            value = value.substring(0, value.length() - 1);
        }
        if (windowsDrive) {
            value =
                    Character.toUpperCase(value.charAt(0))
                            + value.substring(1).toLowerCase(Locale.ROOT);
        } else if (uncPath) {
            value = value.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private static String collapseSeparators(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '/'
                    && normalized.length() > 0
                    && normalized.charAt(normalized.length() - 1) == '/') {
                if (normalized.length() == 1 && value.startsWith("//")) {
                    normalized.append(current);
                }
                continue;
            }
            normalized.append(current);
        }
        return normalized.toString();
    }

    private static boolean isWindowsDriveRoot(String value) {
        return value.length() == 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && value.charAt(2) == '/';
    }
}
