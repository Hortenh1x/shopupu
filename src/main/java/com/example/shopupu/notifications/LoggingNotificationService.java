package com.example.shopupu.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Dev/no-op sender: keeps the notification pipeline testable without SMTP. */
@Slf4j
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.mail.host", havingValue = "never-matches", matchIfMissing = true)
public class LoggingNotificationService implements NotificationService {

    @Override
    public void sendOrderStatusUpdate(String email, String orderNumber, String newStatus) {
        // no PII beyond the recipient needed here; body templates live with real senders
        log.info("Notification: order {} status changed to {} (recipient hash={})",
                orderNumber, newStatus, Integer.toHexString(email == null ? 0 : email.hashCode()));
    }

    @Override
    public void sendPasswordReset(String email, String token) {
        // tokens are secrets: never log them, even in the dev sender
        log.info("Notification: password reset requested (recipient hash={})",
                Integer.toHexString(email == null ? 0 : email.hashCode()));
    }

    @Override
    public void sendEmailVerification(String email, String token) {
        log.info("Notification: email verification issued (recipient hash={})",
                Integer.toHexString(email == null ? 0 : email.hashCode()));
    }
}
