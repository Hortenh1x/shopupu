package com.example.shopupu.identity.dto;

import com.example.shopupu.auth.dto.UserProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** GDPR data export payload (USER-05/COMPL-01). */
public record UserDataExport(
        UserProfile profile,
        List<AddressResponse> addresses,
        List<ExportedOrder> orders,
        List<ExportedReview> reviews,
        Instant exportedAt,
        Map<String, List<Map<String, Object>>> records,
        List<String> scope
) {
    public record ExportedOrder(
            String orderNumber,
            String status,
            BigDecimal paymentAmount,
            Instant createdAt
    ) {
    }

    public record ExportedReview(
            Long productId,
            Integer rating,
            String body,
            String status,
            Instant createdAt,
            String source
    ) {
    }
}
