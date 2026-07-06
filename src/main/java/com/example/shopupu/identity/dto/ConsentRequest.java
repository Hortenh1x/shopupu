package com.example.shopupu.identity.dto;

import com.example.shopupu.identity.entity.UserConsent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsentRequest(
        @NotNull UserConsent.Type consentType,
        @NotNull Boolean granted,
        @NotBlank @Size(max = 32) String policyVersion
) {
}
