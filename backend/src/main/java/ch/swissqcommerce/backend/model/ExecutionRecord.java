package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "execution_record", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id", nullable = false, unique = true)
    private AgentSuggestionEntity suggestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_id", nullable = false)
    private PolicyDecision decision;

    @Column(name = "executed", nullable = false)
    private Boolean executed;

    // jsonb binding: see AgentSuggestionEntity.recommendation — JdbcTypeCode is
    // required so Hibernate binds the String as jsonb, not varchar.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_result", columnDefinition = "jsonb")
    private String executionResult;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "executed_by", length = 50, nullable = false)
    private String executedBy;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;
}
