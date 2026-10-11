package com.matbakhy.operations.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Removes sensitive values from audit text and details. Conservative: when in doubt, redact. */
public final class Redaction {

    public static final String MASK = "[REDACTED]";
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(token|password|secret|otp|code|cookie|authorization|session|phone|email|address|document|pepper).*");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9\\u0660-\\u0669][0-9\\u0660-\\u0669 ()-]{6,}[0-9\\u0660-\\u0669]");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private Redaction() {
    }

    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String out = JWT.matcher(value).replaceAll(MASK);
        out = EMAIL.matcher(out).replaceAll(MASK);
        return PHONE.matcher(out).replaceAll(MASK);
    }

    public static Map<String, Object> details(Map<String, Object> details) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (details == null) {
            return out;
        }
        details.forEach((key, value) -> {
            if (SENSITIVE_KEY.matcher(key).matches()) {
                out.put(key, MASK);
            } else if (value instanceof String s) {
                out.put(key, text(s));
            } else if (value instanceof Number || value instanceof Boolean || value == null) {
                out.put(key, value);
            } else {
                out.put(key, text(String.valueOf(value)));
            }
        });
        return out;
    }
}
