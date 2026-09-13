package com.example.shopupu.common.i18n;

import java.util.List;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/** Only these two UI languages are negotiated; the process default locale is never a fallback. */
public final class SupportedLocales {
    /** Negotiation order; English is the fallback. Also what the storefront config advertises. */
    public static final List<Locale> ALL = List.of(Locale.ENGLISH, Locale.GERMAN);

    private SupportedLocales() {}

    public static List<String> languageTags() {
        return ALL.stream().map(Locale::getLanguage).toList();
    }

    public static Locale fromHeader(String header) {
        if (header == null || header.isBlank()) return Locale.ENGLISH;
        try {
            Locale match = Locale.lookup(Locale.LanguageRange.parse(header), ALL);
            return normalize(match);
        } catch (IllegalArgumentException exception) {
            return Locale.ENGLISH;
        }
    }

    public static Locale normalize(Locale locale) {
        return locale != null && "de".equals(locale.getLanguage()) ? Locale.GERMAN : Locale.ENGLISH;
    }

    public static Locale normalize(String language) {
        return normalize(language == null ? Locale.ENGLISH : Locale.forLanguageTag(language));
    }

    public static Locale current() {
        // Background work without an explicitly captured context is English, regardless of OS locale.
        var context = LocaleContextHolder.getLocaleContext();
        return normalize(context == null ? Locale.ENGLISH : context.getLocale());
    }
}
