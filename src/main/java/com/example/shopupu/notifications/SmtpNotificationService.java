package com.example.shopupu.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Synchronous SMTP adapter selected explicitly; post-commit listeners own dispatch and retry. */
@Service
@ConditionalOnProperty(name = "notifications.provider", havingValue = "smtp")
public class SmtpNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    private final NotificationLinks links;
    private final com.example.shopupu.config.NotificationProperties properties;

    public SmtpNotificationService(JavaMailSender mailSender, MessageSource messageSource,
            NotificationLinks links, com.example.shopupu.config.NotificationProperties properties,
            MailProperties mailProperties) {
        // Boot creates its sender for a present-but-empty host; that is not usable configuration.
        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            throw new IllegalArgumentException("SMTP requires a nonblank spring.mail.host");
        }
        Integer port = mailProperties.getPort();
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("SMTP port must be between 1 and 65535");
        }
        this.mailSender = mailSender;
        this.messageSource = messageSource;
        this.links = links;
        this.properties = properties;
    }

    @Override public boolean isAvailable() { return true; }

    @Override
    public void sendOrderStatusUpdate(String email, String orderNumber, String newStatus) {
        send(email, "notification.order-status.subject", new Object[]{orderNumber},
                "notification.order-status.body", new Object[]{orderNumber, newStatus});
    }

    @Override
    public void sendPasswordReset(String email, String token) {
        send(email, "notification.password-reset.subject", new Object[]{},
                "notification.password-reset.body", new Object[]{links.resetPasswordUrl(token)});
    }

    @Override
    public void sendEmailVerification(String email, String token) {
        send(email, "notification.email-verification.subject", new Object[]{},
                "notification.email-verification.body", new Object[]{links.verifyEmailUrl(token)});
    }

    private void send(String to, String subjectKey, Object[] subjectArgs, String bodyKey, Object[] bodyArgs) {
        var locale = LocaleContextHolder.getLocale();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setFrom(properties.getFrom());
        message.setSubject(messageSource.getMessage(subjectKey, subjectArgs, locale));
        message.setText(messageSource.getMessage(bodyKey, bodyArgs, locale));
        mailSender.send(message);
    }
}
