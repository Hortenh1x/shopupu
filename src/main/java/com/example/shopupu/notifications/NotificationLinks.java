package com.example.shopupu.notifications;

import com.example.shopupu.common.i18n.SupportedLocales;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the frontend deep-links that email bodies point at, from
 * {@code app.frontend-base-url}. Keeping this in one place means every sender
 * (Resend/SMTP) produces the same link and the token is always URL-encoded.
 */
@Component
public class NotificationLinks {

    private final String frontendBaseUrl;

    public NotificationLinks(@Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public String resetPasswordUrl(String token) {
        return frontendBaseUrl + "/reset-password?token=" + encode(token) + langParam();
    }

    public String verifyEmailUrl(String token) {
        return frontendBaseUrl + "/verify-email?token=" + encode(token) + langParam();
    }

    /** The storefront reads {@code lang} from email links so the landing page opens in the email's language. */
    private static String langParam() {
        return "&lang=" + SupportedLocales.current().getLanguage();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
