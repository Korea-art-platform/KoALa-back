package com.koala.koalaback.global.util;

public final class LikeEscapeUtil {
    private static final char ESCAPE_CHAR = '\\';

    private LikeEscapeUtil() {}

    public static String escape(String value) {
        if (value == null) return null;
        return value
                .replace(String.valueOf(ESCAPE_CHAR), ESCAPE_CHAR + String.valueOf(ESCAPE_CHAR))
                .replace("%", ESCAPE_CHAR + "%")
                .replace("_", ESCAPE_CHAR + "_");
    }

    public static String contains(String value) {
        return "%" + escape(value) + "%";
    }
}
