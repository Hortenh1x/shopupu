package com.example.shopupu.notifications;

/**
 * Customer notification port (NOTIF-01). The default implementation only logs;
 * plug an SMTP/provider implementation without touching callers.
 */
public interface NotificationService {

    void sendOrderStatusUpdate(String email, String orderNumber, String newStatus);
}
