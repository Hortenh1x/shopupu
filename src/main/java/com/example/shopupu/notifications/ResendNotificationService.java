package com.example.shopupu.notifications;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Email sender over the Resend HTTP API (NOTIF-01). Active — and preferred over
 * SMTP ({@link Primary}) — as soon as {@code notifications.resend.api-key} is set,
 * so going live is "paste the key". Sends run on the notification pool and never
 * throw into the caller: a mail outage must never fail a shop request (ADR-0003:
 * external HTTP stays off the request/transaction thread).
 */
@Slf4j
@Service
@Primary
@ConditionalOnExpression("'${notifications.resend.api-key:}' != ''")
public class ResendNotificationService implements NotificationService {

    private final MessageSource messageSource;
    private final NotificationLinks links;
    private final String apiKey;
    private final String from;
    private final RestClient restClient;

    public ResendNotificationService(
            MessageSource messageSource,
            NotificationLinks links,
            @Value("${notifications.resend.api-key}") String apiKey,
            @Value("${notifications.resend.from:Shopupu <onboarding@resend.dev>}") String from,
            @Value("${notifications.resend.timeout-seconds:10}") long timeoutSeconds) {
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

    @Override
    @Async("notificationExecutor")
    public void sendOrderStatusUpdate(String email, String orderNumber, String newStatus) {
        send(email,
                message("notification.order-status.subject", orderNumber),
                message("notification.order-status.body", orderNumber, newStatus));
        log.info("Sent order status email for {}", orderNumber);
    }

    @Override
    @Async("notificationExecutor")
    public void sendPasswordReset(String email, String token) {
        send(email,
                message("notification.password-reset.subject"),
                message("notification.password-reset.body", links.resetPasswordUrl(token)));
        log.info("Sent password reset email");
    }

    @Override
    @Async("notificationExecutor")
    public void sendEmailVerification(String email, String token) {
        send(email,
                message("notification.email-verification.subject"),
                message("notification.email-verification.body", links.verifyEmailUrl(token)));
        log.info("Sent email verification email");
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private void send(String to, String subject, String text) {
        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmail(from, List.of(to), subject, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // best-effort side effect: log and swallow, never surface to the caller
            log.warn("Resend email send failed: {}", ex.getMessage());
        }
    }

    private record ResendEmail(String from, List<String> to, String subject, String text) {
    }
}
