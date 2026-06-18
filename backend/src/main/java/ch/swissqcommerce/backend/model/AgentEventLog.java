package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

/**
 * Full audit trail for every agent suggestion and its policy decision.
 * This is the observability backbone: every agent action is logged here
 * regardless of whether it was approved, rejected, or escalated.
 */
@Entity
@Table(name = "agent_event_log", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", length = 50, nullable = false)
    @Builder.Default
    private String eventType = "agent_suggestion";

    @Column(name = "agent", length = 50, nullable = false)
    private String agent;

    @Column(name = "domain", length = 50, nullable = false)
    private String domain;

    @Column(name = "input_summary", columnDefinition = "TEXT")
    private String inputSummary;

    @Column(name = "output_json", columnDefinition = "TEXT", nullable = false)
    private String outputJson;

    @Column(name = "policy_status", length = 30, nullable = false)
    private String policyStatus;

    @Column(name = "policy_reason", columnDefinition = "TEXT")
    private String policyReason;

    @Column(name = "executed", nullable = false)
    @Builder.Default
    private Boolean executed = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
