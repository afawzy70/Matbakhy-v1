package com.matbakhy.shared.web;

import java.util.List;
import java.util.Locale;

/** Response language from Accept-Language: Arabic by default, English only when preferred; malformed headers mean Arabic. */
public final class Languages {

    private Languages() {
    }

    public static boolean prefersEnglish(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return false;
        }
        try {
            return "en".equals(Locale.lookupTag(Locale.LanguageRange.parse(acceptLanguage), List.of("ar", "en")));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
