package com.example.shopupu.notifications;

import com.example.shopupu.common.exception.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Explicitly disabled mail; never claim a reset/verification notification was delivered. */
@Service
@ConditionalOnProperty(name = "notifications.provider", havingValue = "disabled", matchIfMissing = true)
public class LoggingNotificationService implements NotificationService {
    @Override public boolean isAvailable() { return false; }
    @Override public void sendOrderStatusUpdate(String email, String orderNumber, String status) { unavailable(); }
    @Override public void sendPasswordReset(String email, String token) { unavailable(); }
    @Override public void sendEmailVerification(String email, String token) { unavailable(); }
    private void unavailable() { throw new ServiceUnavailableException("EMAIL_DELIVERY_UNAVAILABLE", "Email delivery is unavailable"); }
}
