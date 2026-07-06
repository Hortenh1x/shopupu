package com.example.shopupu.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);

    Page<AuditEvent> findByActor(String actor, Pageable pageable);
}
