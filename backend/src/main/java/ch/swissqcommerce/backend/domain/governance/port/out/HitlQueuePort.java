package ch.swissqcommerce.backend.domain.governance.port.out;

import ch.swissqcommerce.backend.model.HitlQueue;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port giving the governance core read/resolve access to the
 * agent-escalation {@code HitlQueue} table (Phase 8 queue unification), without
 * the core depending on the JPA repository directly (ADR-001).
 */
public interface HitlQueuePort {
    List<HitlQueue> findByStatus(String status);
    Optional<HitlQueue> findByTicketId(String ticketId);
    HitlQueue save(HitlQueue ticket);
    long countByStatus(String status);
}
