package ch.swissqcommerce.backend.domain.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Event-sourced record of a B2B RFQ negotiation outcome (BRD FR-02). Archived to
 * the document store (MongoDB) as an immutable CDC sink alongside the relational
 * b2b_restock_orders / procurement_approvals tables.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationEvent {
    private String eventId;
    private Integer restockOrderId;
    private String wholesalerId;
    private String itemId;
    private BigDecimal proposedPrice;
    private Integer quantity;
    private boolean approved;
    private OffsetDateTime occurredAt;
}
