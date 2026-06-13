package ch.swissqcommerce.backend.domain.governance.core.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * Unified, read-only view of a single human-in-the-loop item for the supervisor console (Phase 8).
 * It merges the two previously-disconnected queues:
 *
 * <ul>
 *   <li><b>B2B_PROCUREMENT</b> — {@code ProcurementApproval} rows (restock-order overrides flagged
 *       by the procurement guardrails), id {@code "PA-<id>"}.
 *   <li><b>AGENT_ESCALATION</b> — {@code HitlQueue} rows (budget / low-confidence escalations and
 *       pricing-review flags), id {@code "AQ-<ticketId>"}.
 * </ul>
 *
 * The composite {@link #id} prefix is what {@code resolveHitlItem} routes on, so the console can
 * approve/reject either source through one endpoint.
 */
@Data
@Builder
public class HitlItem {
    private String id;
    private String source;
    private String type;
    private String status; // normalized: PENDING | APPROVED | REJECTED
    private BigDecimal amount;
    private String description;
    private String reference; // restock-order id / order id, for operator context
    private OffsetDateTime createdAt;
}
