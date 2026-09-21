package com.example.shopupu.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Period;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Data retention periods (LEG-06). The defaults are the periods published on the storefront's
 * "Data and privacy" page; changing one here without changing the page makes the page untrue.
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "retention")
public class RetentionProperties {

    private boolean enabled = true;

    /** Security/audit events are dropped after this period. */
    @NotNull
    private Period auditEvents = Period.ofMonths(12);

    /** Customer accounts without a sign-in for this period are erased like a GDPR request. */
    @NotNull
    private Period inactiveAccounts = Period.ofMonths(12);

    /** Pseudonymised order/payment history of erased accounts is dropped after this period. */
    @NotNull
    private Period erasedAccountRecords = Period.ofMonths(24);

    /** Rows handled per transaction; the job loops until a pass finds nothing. */
    @Min(1)
    @Max(10_000)
    private int batchSize = 500;

    @AssertTrue(message = "retention periods must be positive")
    public boolean isPeriodsPositive() {
        return positive(auditEvents) && positive(inactiveAccounts) && positive(erasedAccountRecords);
    }

    private static boolean positive(Period period) {
        return period != null && !period.isNegative() && !period.isZero();
    }
}
