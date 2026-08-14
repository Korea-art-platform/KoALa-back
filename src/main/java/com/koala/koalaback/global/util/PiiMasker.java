package com.koala.koalaback.global.util;

public final class PiiMasker {
    private PiiMasker() {}

    public static String email(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        if (at <= 0) return "***";

        String local = email.substring(0, at);
        String domain = email.substring(at);

        if (local.length() == 1) return "*" + domain;
        return local.charAt(0) + "*".repeat(Math.max(1, local.length() - 1)) + domain;
    }

    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) return "(blank)";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) return "***";

        String prefix = digits.substring(0, 3);
        String suffix = digits.substring(digits.length() - 4);
        return prefix + "****" + suffix;
    }

    public static String name(String name) {
        if (name == null || name.isBlank()) return "(blank)";
        if (name.length() == 1) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
