package com.example.shopupu.notifications;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Synchronous adapter. Post-commit listeners own dispatch/retry and observe any provider failure. */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notifications.provider", havingValue = "resend")
public class ResendNotificationService implements NotificationService {

    private final MessageSource messageSource;
    private final NotificationLinks links;
    private final String apiKey;
    private final String from;
    private final RestClient restClient;

    public ResendNotificationService(
            MessageSource messageSource,
            NotificationLinks links,
            com.example.shopupu.config.NotificationProperties properties) {
        String apiKey = properties.getResend().getApiKey();
        String from = properties.getResend().getFrom();
        long timeoutSeconds = properties.getResend().getTimeoutSeconds();
        this.messageSource = messageSource;
        this.links = links;
        this.apiKey = apiKey;
        this.from = from;
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override public boolean isAvailable() { return true; }

    @Override
    public void sendOrderStatusUpdate(String email, String orderNumber, String newStatus) {
        send(email,
                message("notification.order-status.subject", orderNumber),
                message("notification.order-status.body", orderNumber, newStatus));
    }

    @Override
    public void sendPasswordReset(String email, String token) {
        send(email,
                message("notification.password-reset.subject"),
                message("notification.password-reset.body", links.resetPasswordUrl(token)));
    }

    @Override
    public void sendEmailVerification(String email, String token) {
        send(email,
                message("notification.email-verification.subject"),
                message("notification.email-verification.body", links.verifyEmailUrl(token)));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private void send(String to, String subject, String text) {
        restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmail(from, List.of(to), subject, text))
                    .retrieve()
                    .toBodilessEntity();
    }

    private record ResendEmail(String from, List<String> to, String subject, String text) {
    }
}
