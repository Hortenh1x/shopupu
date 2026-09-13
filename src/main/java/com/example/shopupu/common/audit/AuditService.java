package com.example.shopupu.common.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central audit trail (SEC-14/AUTHZ-05). Success records share the business
 * transaction, so a rollback cannot leave a false success or need a second connection.
 * Call failed-attempt auditing after the failed business transaction has ended.
 * Persistence failure is deliberately fail-closed; it is never swallowed inside a transaction.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void record(String actor, String eventType, String targetType, String targetId, String details) {
        auditEventRepository.save(AuditEvent.builder()
                .actor(actor)
                .eventType(eventType)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build());
    }

    /** Stable pseudonymous account key shared by failed-login audit and local export/erasure. */
    public static String accountActor(String email) {
        try {
            byte[] normalized = email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return "account-sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
