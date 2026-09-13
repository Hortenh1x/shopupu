package com.example.shopupu.notifications;

/**
 * Customer notification port. Disabled by default; availability means a real sender is configured.
 * Invoke delivery only from post-commit listeners, outside database transactions.
 */
public interface NotificationService {

    /** True means a real sender is configured, not proven inbox deliverability. */
    default boolean isAvailable() { return false; }

    void sendOrderStatusUpdate(String email, String orderNumber, String newStatus);

    /** One-time reset token; the frontend embeds it into its reset page link. */
    void sendPasswordReset(String email, String token);

    /** One-time verification token for a freshly registered address. */
    void sendEmailVerification(String email, String token);
}
