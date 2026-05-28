package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "security_trust_ledger", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityTrustLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Integer auditId;

    @Column(name = "timestamp", insertable = false, updatable = false)
    private OffsetDateTime timestamp;

    @Column(name = "actor_type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String actorType;

    @Column(name = "actor_id", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String actorId;

    @Column(name = "event", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String event;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "current_value", nullable = false)
    @Min(0)
    @Max(100)
    private Integer currentValue;
}
