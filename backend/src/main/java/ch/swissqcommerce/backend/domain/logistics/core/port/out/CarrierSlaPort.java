package ch.swissqcommerce.backend.domain.logistics.core.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outbound port giving the logistics core access to carrier SLA rules without coupling to JPA
 * entities (hexagonal architecture / ADR-001).
 */
public interface CarrierSlaPort {

    /** Carrier SLA data projected for use by the logistics core. */
    record CarrierSlaData(
            String carrier,
            BigDecimal maxWeightKg,
            int standardDays,
            int expressDays,
            boolean fragileOk) {}

    /** Returns all active carrier SLA rules. */
    List<CarrierSlaData> findActiveSlas();
}
