package com.example.shopupu.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Real email sender (NOTIF-01), active only when SMTP is configured
 * (spring.mail.host). Subjects/bodies come from messages*.properties (I18N-01).
 * Sends run on the notification pool so the network call never blocks (or holds
 * a DB connection inside) the caller's transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    private final NotificationLinks links;

    @Override
    @Async("notificationExecutor")
    public void sendOrderStatusUpdate(String email, String orderNumber, String newStatus) {
        send(email, "notification.order-status.subject", new Object[]{orderNumber},
                "notification.order-status.body", new Object[]{orderNumber, newStatus});
        log.info("Sent order status email for {}", orderNumber);
    }

    @Override
    @Async("notificationExecutor")
    public void sendPasswordReset(String email, String token) {
        send(email, "notification.password-reset.subject", new Object[]{},
                "notification.password-reset.body", new Object[]{links.resetPasswordUrl(token)});
        log.info("Sent password reset email");
    }

    @Override
    @Async("notificationExecutor")
    public void sendEmailVerification(String email, String token) {
        send(email, "notification.email-verification.subject", new Object[]{},
                "notification.email-verification.body", new Object[]{links.verifyEmailUrl(token)});
        log.info("Sent email verification email");
    }

    private void send(String to, String subjectKey, Object[] subjectArgs, String bodyKey, Object[] bodyArgs) {
        var locale = LocaleContextHolder.getLocale();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(messageSource.getMessage(subjectKey, subjectArgs, locale));
        message.setText(messageSource.getMessage(bodyKey, bodyArgs, locale));
        mailSender.send(message);
    }
}
