package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_events", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "aggregate_type")
    private String aggregateType;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status")
    @Builder.Default
    private String status = "PENDING"; // PENDING, PUBLISHED, FAILED

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
