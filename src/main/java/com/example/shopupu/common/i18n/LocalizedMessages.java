package com.example.shopupu.common.i18n;

import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;

/** Shared by MVC and security filters, which run before MVC installs a locale context. */
public final class LocalizedMessages {
    private static final ResourceBundleMessageSource SOURCE = createSource();
    private static final Map<String, String> DETAILS = Map.ofEntries(
            Map.entry("Wrong login or password", "credentials"),
            Map.entry("Current password is incorrect", "current-password"),
            Map.entry("User with this email already exists", "email-exists"),
            Map.entry("Token is invalid or expired", "token"),
            Map.entry("Invalid refresh token", "session"),
            Map.entry("Sign in with MFA to continue", "mfa-required"),
            Map.entry("MFA challenge or code is invalid or expired", "mfa-invalid"),
            Map.entry("Password must contain at least 15 characters", "password-min"),
            Map.entry("Password must be at most 72 UTF-8 bytes", "password-max"),
            Map.entry("Choose a less common password", "password-common"),
            Map.entry("Google login is not configured", "google-disabled"),
            Map.entry("Could not verify Google token", "google-invalid"),
            Map.entry("Invalid Google token", "google-invalid"),
            Map.entry("Google account email is not verified", "google-unverified"),
            Map.entry("Account is disabled", "account-unavailable"),
            Map.entry("Account is unavailable", "account-unavailable"),
            Map.entry("Invalid account", "account-unavailable"),
            Map.entry("Cart is empty - nothing to order", "cart-empty"),
            Map.entry("Payment is already in progress", "payment-pending"),
            Map.entry("Only unpaid orders can be paid", "order-unpaid"),
            Map.entry("Shipping must be selected before payment", "shipping-required"),
            Map.entry("Shipping method must be selected before payment", "shipping-required"),
            Map.entry("Complete delivery address is required before payment", "address-required"),
            Map.entry("Order can no longer be cancelled; request a refund instead", "cancel-unavailable"),
            Map.entry("A payment or refund is unresolved; wait for provider confirmation", "payment-unresolved"),
            Map.entry("Image file is required", "image-required"),
            Map.entry("Only jpeg, png, webp, and gif images are allowed", "image-type"),
            Map.entry("Malformed request body", "malformed"));

    private LocalizedMessages() {}

    private static ResourceBundleMessageSource createSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    public static MessageSource source() { return SOURCE; }

    public static String text(String key, Object... arguments) {
        return text(SupportedLocales.current(), key, arguments);
    }

    public static String text(Locale locale, String key, Object... arguments) {
        return SOURCE.getMessage(key, arguments, SupportedLocales.normalize(locale));
    }

    public static String title(HttpStatus status, Locale locale) {
        return SOURCE.getMessage("problem.title." + status.value(), null, status.getReasonPhrase(), locale);
    }

    public static String detail(String code, String original, Locale locale) {
        // Preserve existing English domain detail. German never claims an untranslated string is localized.
        if (!Locale.GERMAN.equals(locale) && original != null && !original.isBlank()) return original;
        String key = original == null ? null : DETAILS.get(original);
        if (key != null) return text(locale, "problem.detail." + key);
        return SOURCE.getMessage("problem.code." + code, null,
                text(locale, "problem.code.BAD_REQUEST"), locale);
    }

    public static String fieldError(FieldError error, Locale locale) {
        if (!Locale.GERMAN.equals(locale)) return error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage();
        String known = DETAILS.get(error.getDefaultMessage() == null ? "" : error.getDefaultMessage());
        if (known != null) return text(locale, "problem.detail." + known);
        return SOURCE.getMessage("validation." + error.getCode(), null,
                text(locale, "validation.invalid"), locale);
    }
}
