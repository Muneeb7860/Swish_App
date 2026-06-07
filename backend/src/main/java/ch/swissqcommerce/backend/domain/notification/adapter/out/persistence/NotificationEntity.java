package ch.swissqcommerce.backend.domain.notification.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {
    @Id
    private String notificationId;
    private String recipientId;
    private String channel;
    private String subject;
    private String body;
    private String status;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;
}
