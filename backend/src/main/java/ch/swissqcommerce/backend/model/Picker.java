package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pickers", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Picker {

    @Id
    @Column(name = "picker_id", length = 50)
    @Size(max = 50)
    private String pickerId;

    @Column(name = "full_name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Column(name = "trust_score", nullable = false)
    @Min(0)
    @Max(100)
    @Builder.Default
    private Integer trustScore = 100;

    @Column(name = "lightning_badge", nullable = false)
    @Builder.Default
    private Boolean lightningBadge = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_store_id")
    private DarkStore activeStore;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
