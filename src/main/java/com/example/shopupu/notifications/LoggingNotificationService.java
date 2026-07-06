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
}
