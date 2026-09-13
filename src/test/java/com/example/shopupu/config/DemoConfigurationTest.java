package com.example.shopupu.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.shopupu.notifications.NotificationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class DemoConfigurationTest {
    @Test
    void liveKeysLegacyProvidersAndRealStoreModeAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var properties = new PaymentProperties();
            properties.setCallbackUrl("http://localhost:8080/api/v1/payments/callback");
            assertThat(validator.validate(properties)).isEmpty();
            properties.setCurrency("USD");
            assertThat(validator.validate(properties)).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
            properties.setCurrency("EUR");
            var shipping = new ShippingProperties();
            shipping.setCurrency("USD");
            assertThat(validator.validate(shipping)).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
            shipping.setCurrency("EUR");
            shipping.getRates().setDhl(new java.math.BigDecimal("-1"));
            assertThat(validator.validate(shipping)).anyMatch(v -> v.getPropertyPath().toString().equals("rates.dhl"));
            properties.getStripe().setSecretKey("sk_live_example");
            assertThat(validator.validate(properties)).anyMatch(v -> v.getPropertyPath().toString().equals("stripe.secretKey"));
            properties.getStripe().setSecretKey("");
            properties.setDefaultProvider("monobank");
            assertThat(validator.validate(properties)).anyMatch(v -> v.getPropertyPath().toString().equals("defaultProvider"));
            var demo = new DemoProperties();
            demo.setEnabled(false);
            assertThat(validator.validate(demo)).isNotEmpty();
        }
    }

    @Test
    void capabilitiesDistinguishLocalSimulationUnconnectedStripeAndConfiguredServices() {
        var properties = new PaymentProperties();
        var notifications = mock(NotificationService.class);
        var controller = new StorefrontConfigController(properties, new DemoProperties(), new AiProperties(), notifications);
        assertThat(controller.configuration().fictionalProducts()).isTrue();
        assertThat(controller.configuration().payments().mode()).isEqualTo("LOCAL_SIMULATION");
        assertThat(controller.configuration().email().available()).isFalse();
        properties.setDefaultProvider("stripe");
        assertThat(controller.configuration().payments().available()).isFalse();
        assertThat(controller.configuration().payments().mode()).isEqualTo("UNAVAILABLE");
        properties.getStripe().setSecretKey("sk_test_fixture");
        properties.getStripe().setWebhookSecret("whsec_fixture");
        when(notifications.isAvailable()).thenReturn(true);
        assertThat(controller.configuration().payments().mode()).isEqualTo("STRIPE_TEST");
        assertThat(controller.configuration().payments().testMode()).isTrue();
        assertThat(controller.configuration().email().available()).isTrue();
    }
}
