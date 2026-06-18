package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "outcome_record", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutcomeRecord {

    @Id
    @Column(name = "suggestion_id")
    private UUID suggestionId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "suggestion_id")
    private AgentSuggestionEntity suggestion;

    @Column(name = "measurement_window", length = 100, nullable = false)
    private String measurementWindow;

    @Column(name = "metrics", length = 4000, nullable = false)
    private String metrics;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "evaluated_at", insertable = false, updatable = false)
    private OffsetDateTime evaluatedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
