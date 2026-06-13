package ch.swissqcommerce.backend.domain.event.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "domain_events", schema = "oltp")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventEntity {
    @Id private String eventId;
    private String aggregateId;
    private String aggregateType;
    private String eventType;
    private String payload;
    private OffsetDateTime createdAt;
    private OffsetDateTime processedAt;
    private String status;
}
