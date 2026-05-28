package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chaos_fault_logs", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChaosFaultLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fault_id")
    private Integer faultId;

    @Column(name = "fault_type", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String faultType;

    @Column(name = "triggered_at", insertable = false, updatable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
}
