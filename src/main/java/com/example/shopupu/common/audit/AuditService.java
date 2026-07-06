package com.example.shopupu.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central audit trail (SEC-14/AUTHZ-05). REQUIRES_NEW so an audit row survives
 * a business rollback (e.g. failed login attempts are still recorded).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String eventType, String targetType, String targetId, String details) {
        try {
            auditEventRepository.save(AuditEvent.builder()
                    .actor(actor)
                    .eventType(eventType)
                    .targetType(targetType)
                    .targetId(targetId)
                    .details(details)
                    .build());
        } catch (Exception ex) {
            // auditing must never break the business flow
            log.error("Failed to record audit event {} for {}", eventType, actor, ex);
        }
    }
}
