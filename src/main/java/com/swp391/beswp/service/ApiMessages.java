package com.swp391.beswp.service;

import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Resolves API messages from Accept-Language; defaults to Vietnamese outside HTTP requests. */
public final class ApiMessages {
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi");

    private ApiMessages() { }

    public static String text(String key, Object... arguments) {
        Locale locale = LocaleContextHolder.getLocaleContext() == null
                ? DEFAULT_LOCALE : LocaleContextHolder.getLocale();
        try {
            String pattern = ResourceBundle.getBundle("messages", locale).getString(key);
            return new MessageFormat(pattern, locale).format(arguments);
        } catch (MissingResourceException exception) {
            throw new IllegalArgumentException("Missing API message: " + key, exception);
        }
    }
}
