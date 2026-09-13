package com.example.shopupu.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "auth.mfa")
public class MfaProperties {
    /** Empty means MFA is unavailable and privileged login fails closed with a clear 503. */
    private String encryptionKey = "";
    @NotBlank private String issuer = "Shopupu Demo";
    @AssertTrue(message = "auth.mfa.encryption-key must be empty or Base64 encoding of exactly 32 bytes")
    public boolean isEncryptionKeyValid() {
        if (encryptionKey == null || encryptionKey.isBlank()) return true;
        try { return Base64.getDecoder().decode(encryptionKey).length == 32; }
        catch (IllegalArgumentException ex) { return false; }
    }
}
