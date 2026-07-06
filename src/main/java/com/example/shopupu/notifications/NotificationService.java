package com.example.shopupu.notifications;

/**
 * Customer notification port (NOTIF-01). The default implementation only logs;
 * plug an SMTP/provider implementation without touching callers.
 */
public interface NotificationService {

    void sendOrderStatusUpdate(String email, String orderNumber, String newStatus);

    /** One-time reset token; the frontend embeds it into its reset page link. */
    void sendPasswordReset(String email, String token);

    /** One-time verification token for a freshly registered address. */
    void sendEmailVerification(String email, String token);
}
