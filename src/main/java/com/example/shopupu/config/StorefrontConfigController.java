package com.example.shopupu.config;

import com.example.shopupu.common.i18n.SupportedLocales;
import com.example.shopupu.notifications.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public availability only; never exposes provider keys, webhook secrets or internal URLs. */
@RestController
@RequiredArgsConstructor
public class StorefrontConfigController {
    private final PaymentProperties paymentProperties;
    private final DemoProperties demoProperties;
    private final AiProperties aiProperties;
    private final NotificationService notifications;
    @Value("${google.client-id:}")
    private String googleClientId;

    @GetMapping("/api/v1/storefront/config")
    public StorefrontConfig configuration() {
        String provider = paymentProperties.getDefaultProvider();
        boolean available = "stub".equals(provider) || "stripe".equals(provider) && paymentProperties.getStripe().isAvailable();
        String mode = !available ? "UNAVAILABLE" : "stub".equals(provider) ? "LOCAL_SIMULATION" : "STRIPE_TEST";
        return new StorefrontConfig(demoProperties.isEnabled(), true, SupportedLocales.languageTags(),
                new Payments(provider, mode, available, true), new Feature(notifications.isAvailable()),
                new Feature(googleClientId != null && !googleClientId.isBlank()), new Feature(aiProperties.isEnabled()));
    }

    public record StorefrontConfig(boolean demoMode, boolean fictionalProducts, List<String> supportedLocales,
            Payments payments, Feature email, Feature google, Feature ai) {}
    public record Payments(String provider, String mode, boolean available, boolean testMode) {}
    public record Feature(boolean available) {}
}
