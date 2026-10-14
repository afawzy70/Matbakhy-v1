package com.matbakhy.identity.api;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizes Egyptian mobile numbers to E.164 (+20 1x xxxx xxxx). Accepts local (01x...), international
 * (+20 / 0020) forms, spaces, dashes and Arabic-Indic or Eastern Arabic digits. Landlines and other
 * countries are rejected because verification codes are delivered by SMS to mobile numbers.
 */
public final class EgyptianPhone {

    private static final Pattern MOBILE = Pattern.compile("\\+201[0125][0-9]{8}");

    private EgyptianPhone() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.length() > 32) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder();
        boolean plus = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '+' && digits.isEmpty() && !plus) {
                plus = true;
            } else if (Character.isDigit(c)) {
                int value = Character.digit(c, 10);
                if (value < 0) {
                    return Optional.empty();
                }
                digits.append(value);
            } else if (c != ' ' && c != '-' && c != '(' && c != ')') {
                return Optional.empty();
            }
        }
        String d = digits.toString();
        String e164;
        if (plus) {
            e164 = "+" + d;
        } else if (d.startsWith("0020")) {
            e164 = "+" + d.substring(2);
        } else if (d.startsWith("20") && d.length() == 12) {
            e164 = "+" + d;
        } else if (d.startsWith("0") && d.length() == 11) {
            e164 = "+20" + d.substring(1);
        } else {
            return Optional.empty();
        }
        return MOBILE.matcher(e164).matches() ? Optional.of(e164) : Optional.empty();
    }
}
