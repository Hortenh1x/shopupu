package com.example.shopupu.identity.dto;

import com.example.shopupu.identity.entity.UserConsent;
import java.time.Instant;

public record ConsentResponse(
        UserConsent.Type consentType,
        boolean granted,
        String policyVersion,
        Instant createdAt
) {
    public static ConsentResponse from(UserConsent consent) {
        return new ConsentResponse(
                consent.getConsentType(),
                consent.isGranted(),
                consent.getPolicyVersion(),
                consent.getCreatedAt()
        );
    }
}
