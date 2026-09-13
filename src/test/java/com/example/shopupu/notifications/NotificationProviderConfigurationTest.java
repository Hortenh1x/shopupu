package com.example.shopupu.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shopupu.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mail.javamail.JavaMailSender;

/** Configuration only: creates adapters without sending mail or contacting any provider. */
class NotificationProviderConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(Providers.class);

    @Test
    void disabledRemainsTheOnlyProviderWithBootMailBeanFromEmptyHost() {
        runner.withPropertyValues("spring.mail.host=").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JavaMailSender.class)
                    .hasSingleBean(NotificationService.class).hasSingleBean(LoggingNotificationService.class)
                    .doesNotHaveBean(SmtpNotificationService.class).doesNotHaveBean(ResendNotificationService.class);
            assertThat(context.getBean(NotificationService.class).isAvailable()).isFalse();
        });
    }

    @Test
    void disabledDoesNotRequireAnyMailBean() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(LoggingNotificationService.class)
                .doesNotHaveBean(JavaMailSender.class));
    }

    @Test
    void smtpWithoutHostPropertyCannotStart() {
        runner.withPropertyValues("notifications.provider=smtp", "notifications.from=demo@example.test")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void smtpRejectsBlankHostEvenWhenBootCreatesMailBean(String host) {
        runner.withPropertyValues("notifications.provider=smtp", "notifications.from=demo@example.test",
                "spring.mail.host=" + host).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("SMTP requires a nonblank spring.mail.host");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65536})
    void smtpRejectsInvalidPort(int port) {
        runner.withPropertyValues("notifications.provider=smtp", "notifications.from=demo@example.test",
                "spring.mail.host=smtp.example.test", "spring.mail.port=" + port).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("SMTP port must be between 1 and 65535");
        });
    }

    @Test
    void validSmtpConfigurationSelectsOneAvailableProviderWithoutConnecting() {
        runner.withPropertyValues("notifications.provider=smtp", "notifications.from=demo@example.test",
                "spring.mail.host=smtp.example.test", "spring.mail.port=587").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NotificationService.class)
                    .hasSingleBean(SmtpNotificationService.class).doesNotHaveBean(LoggingNotificationService.class)
                    .doesNotHaveBean(ResendNotificationService.class);
            assertThat(context.getBean(NotificationService.class).isAvailable()).isTrue();
        });
    }

    @Test
    void resendSelectionDoesNotRequireSmtpHost() {
        runner.withPropertyValues("notifications.provider=resend", "notifications.resend.api-key=offline-fixture",
                "notifications.resend.from=demo@example.test", "spring.mail.host=").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NotificationService.class)
                    .hasSingleBean(ResendNotificationService.class).doesNotHaveBean(SmtpNotificationService.class)
                    .doesNotHaveBean(LoggingNotificationService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
    @Import({LoggingNotificationService.class, SmtpNotificationService.class, ResendNotificationService.class})
    static class Providers {
        @Bean MessageSource messageSource() { return new StaticMessageSource(); }
        @Bean NotificationLinks notificationLinks() { return new NotificationLinks("https://example.test"); }
    }
}
