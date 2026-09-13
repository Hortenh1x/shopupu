package com.example.shopupu.common.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuditServiceTest {
    @Test
    void auditFailureIsPropagatedInsteadOfPretendingTheBusinessTransactionCanCommit() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        var failure = new DataIntegrityViolationException("offline audit fixture");
        when(events.save(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> new AuditService(events).record("actor", "EVENT", "user", "1", null)));
    }

    @Test
    void accountActorNormalizesAndPseudonymizesEmailWithAnExportableStableKey() {
        String key = AuditService.accountActor("  TEST@Example.com ");
        assertEquals(AuditService.accountActor("test@example.com"), key);
        assertTrue(key.matches("account-sha256:[0-9a-f]{64}"));
        assertFalse(key.contains("test@example.com"));
        assertNotEquals(AuditService.accountActor("another@example.com"), key);
    }
}
