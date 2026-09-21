package com.example.shopupu.identity.service;

import com.example.shopupu.common.audit.AuditEventRepository;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.config.RetentionProperties;
import com.example.shopupu.identity.repository.PersonalDataRepository;
import com.example.shopupu.identity.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Enforces the retention periods the privacy page promises (LEG-06): audit events, inactive
 * customer accounts and the pseudonymised history of erased accounts. Runs after the nightly
 * backup (03:40 UTC on the production host), so a backup can still hold purged rows for the
 * 14-day backup rotation the page also states. Every batch is its own transaction and a failing
 * account never stops the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final RetentionProperties properties;
    private final AuditEventRepository auditEvents;
    private final UserRepository users;
    private final PersonalDataRepository personalData;
    private final GdprService gdpr;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    @Scheduled(cron = "0 10 4 * * *", zone = "UTC")
    public void run() {
        if (!properties.isEnabled()) {
            return;
        }
        run(Instant.now());
    }

    /** The clock is a parameter so tests can move it; production always passes now(). */
    public RetentionReport run(Instant now) {
        int events = purgeAuditEvents(cutoff(now, properties.getAuditEvents()));
        int erased = eraseInactiveAccounts(cutoff(now, properties.getInactiveAccounts()));
        int dropped = purgeErasedAccounts(cutoff(now, properties.getErasedAccountRecords()));
        if (events > 0 || erased > 0 || dropped > 0) {
            log.info("Retention: purged {} audit events, erased {} inactive accounts, dropped {} erased accounts",
                    events, erased, dropped);
        }
        return new RetentionReport(events, erased, dropped);
    }

    private int purgeAuditEvents(Instant cutoff) {
        int total = 0;
        int removed;
        do {
            removed = transactions.execute(tx -> auditEvents.deleteCreatedBefore(cutoff, properties.getBatchSize()));
            total += removed;
        } while (removed == properties.getBatchSize());
        return total;
    }

    private int eraseInactiveAccounts(Instant cutoff) {
        int total = 0;
        List<Long> ids;
        do {
            ids = users.findInactiveCustomerIds(cutoff, PageRequest.of(0, properties.getBatchSize()));
            for (Long id : ids) {
                try {
                    transactions.executeWithoutResult(tx -> {
                        users.findById(id).ifPresent(gdpr::anonymizeAccount);
                        audit.record("retention", "RETENTION_INACTIVE_ACCOUNT_ERASED", "user", Long.toString(id),
                                "No sign-in since " + cutoff);
                    });
                    total++;
                } catch (RuntimeException ex) {
                    // Erasure re-runs tomorrow; a stuck account must not hide the rest of the batch.
                    log.warn("Retention: could not erase inactive account {}: {}", id, ex.toString());
                }
            }
            // anonymizeAccount sets deletedAt, so a finished batch leaves the query; a failed one is skipped by size.
        } while (ids.size() == properties.getBatchSize() && total > 0);
        return total;
    }

    private int purgeErasedAccounts(Instant cutoff) {
        int total = 0;
        List<Long> ids;
        do {
            ids = users.findErasedBefore(cutoff, PageRequest.of(0, properties.getBatchSize()));
            for (Long id : ids) {
                Integer dropped = transactions.execute(tx -> personalData.deleteErasedAccount(id, cutoff));
                total += dropped == null ? 0 : dropped;
            }
        } while (ids.size() == properties.getBatchSize());
        return total;
    }

    private static Instant cutoff(Instant now, java.time.Period period) {
        return ZonedDateTime.ofInstant(now, ZoneOffset.UTC).minus(period).toInstant();
    }

    public record RetentionReport(int auditEventsPurged, int inactiveAccountsErased, int erasedAccountsDropped) {
    }
}
