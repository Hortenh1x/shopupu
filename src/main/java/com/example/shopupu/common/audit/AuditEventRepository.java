package com.example.shopupu.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);

    Page<AuditEvent> findByActor(String actor, Pageable pageable);

    /** Retention (LEG-06): bounded batches keep the nightly purge from holding a long lock. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            delete from audit_events where id in (
                select id from audit_events where created_at < :cutoff order by id limit :batch)""", nativeQuery = true)
    int deleteCreatedBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("batch") int batch);
}
