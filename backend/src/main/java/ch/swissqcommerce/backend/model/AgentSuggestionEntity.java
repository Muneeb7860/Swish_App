package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "agent_suggestion", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentSuggestionEntity {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "trace_id", columnDefinition = "UUID", nullable = false)
    private UUID traceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_name", referencedColumnName = "name", nullable = false)
    private AgentRegistry agent;

    @Column(name = "domain", length = 50, nullable = false)
    private String domain;

    @Column(name = "entity_id", length = 100, nullable = false)
    private String entityId;

    @Column(name = "recommendation", columnDefinition = "jsonb", nullable = false)
    private String recommendation;

    @Column(name = "confidence", precision = 3, scale = 2, nullable = false)
    private BigDecimal confidence;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "impact", length = 20, nullable = false)
    private String impact; // low, medium, high

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "pending"; // pending, approved, rejected, executed, failed, expired

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
