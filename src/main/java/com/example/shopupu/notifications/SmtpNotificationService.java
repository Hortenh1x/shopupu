package com.example.shopupu.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real email sender (NOTIF-01), active only when SMTP is configured
 * (spring.mail.host). Subjects/bodies come from messages*.properties (I18N-01).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;

    @Override
    public void sendOrderStatusUpdate(String email, String orderNumber, String newStatus) {
        var locale = LocaleContextHolder.getLocale();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(messageSource.getMessage(
                "notification.order-status.subject", new Object[]{orderNumber}, locale));
        message.setText(messageSource.getMessage(
                "notification.order-status.body", new Object[]{orderNumber, newStatus}, locale));
        mailSender.send(message);
        log.info("Sent order status email for {}", orderNumber);
    }
}
