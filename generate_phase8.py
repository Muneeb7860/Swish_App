import os

base_path = "backend/src/main/java/ch/swissqcommerce/backend/domain/notification"

dirs = [
    f"{base_path}/core/model",
    f"{base_path}/core/service",
    f"{base_path}/port/in",
    f"{base_path}/port/out",
    f"{base_path}/adapter/in/event",
    f"{base_path}/adapter/out/persistence",
]

for d in dirs:
    os.makedirs(d, exist_ok=True)

files = {
    f"{base_path}/core/model/Notification.java": """package ch.swissqcommerce.backend.domain.notification.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private String notificationId;
    private String recipientId;
    private String channel; // SMS, EMAIL, PUSH
    private String subject;
    private String body;
    private String status; // PENDING, SENT, FAILED
    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;
}
""",
    f"{base_path}/port/in/NotificationUseCase.java": """package ch.swissqcommerce.backend.domain.notification.port.in;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;

public interface NotificationUseCase {
    Notification scheduleNotification(String recipientId, String channel, String subject, String body);
    void sendPendingNotifications();
}
""",
    f"{base_path}/port/out/NotificationPort.java": """package ch.swissqcommerce.backend.domain.notification.port.out;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import java.util.List;

public interface NotificationPort {
    Notification save(Notification notification);
    List<Notification> findPending();
    void dispatch(Notification notification);
}
""",
    f"{base_path}/core/service/NotificationServiceImpl.java": """package ch.swissqcommerce.backend.domain.notification.core.service;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import ch.swissqcommerce.backend.domain.notification.port.in.NotificationUseCase;
import ch.swissqcommerce.backend.domain.notification.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationUseCase {
    private final NotificationPort notificationPort;

    @Override
    public Notification scheduleNotification(String recipientId, String channel, String subject, String body) {
        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID().toString())
                .recipientId(recipientId)
                .channel(channel)
                .subject(subject)
                .body(body)
                .status("PENDING")
                .scheduledAt(OffsetDateTime.now())
                .build();
        return notificationPort.save(notification);
    }

    @Override
    public void sendPendingNotifications() {
        List<Notification> pending = notificationPort.findPending();
        for (Notification n : pending) {
            try {
                notificationPort.dispatch(n);
                n.setStatus("SENT");
                n.setSentAt(OffsetDateTime.now());
            } catch (Exception e) {
                n.setStatus("FAILED");
            }
            notificationPort.save(n);
        }
    }
}
""",
    f"{base_path}/adapter/out/persistence/NotificationEntity.java": """package ch.swissqcommerce.backend.domain.notification.adapter.out.persistence;

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
""",
    f"{base_path}/adapter/out/persistence/NotificationRepository.java": """package ch.swissqcommerce.backend.domain.notification.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
    List<NotificationEntity> findByStatus(String status);
}
""",
    f"{base_path}/adapter/out/persistence/NotificationPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.notification.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import ch.swissqcommerce.backend.domain.notification.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationPort {
    private final NotificationRepository repository;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = NotificationEntity.builder()
                .notificationId(notification.getNotificationId())
                .recipientId(notification.getRecipientId())
                .channel(notification.getChannel())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .scheduledAt(notification.getScheduledAt())
                .setSentAt(notification.getSentAt())
                .build();
        repository.save(entity);
        return notification;
    }

    @Override
    public List<Notification> findPending() {
        return repository.findByStatus("PENDING").stream()
                .map(e -> Notification.builder()
                        .notificationId(e.getNotificationId())
                        .recipientId(e.getRecipientId())
                        .channel(e.getChannel())
                        .subject(e.getSubject())
                        .body(e.getBody())
                        .status(e.getStatus())
                        .scheduledAt(e.getScheduledAt())
                        .sentAt(e.getSentAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void dispatch(Notification notification) {
        // Mock third party dispatch (SendGrid/Twilio)
        System.out.println("Dispatching " + notification.getChannel() + " to " + notification.getRecipientId());
    }
}
""",
    f"{base_path}/adapter/in/event/NotificationEventListener.java": """package ch.swissqcommerce.backend.domain.notification.adapter.in.event;

import ch.swissqcommerce.backend.domain.notification.port.in.NotificationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import ch.swissqcommerce.backend.domain.event.core.model.BaseDomainEvent;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationUseCase notificationUseCase;

    // Fully Async Event Listener - no direct coupling!
    @EventListener
    public void handleDomainEvent(BaseDomainEvent event) {
        if ("OrderPlaced".equals(event.getEventType())) {
            notificationUseCase.scheduleNotification(event.getAggregateId(), "EMAIL", "Order Placed", "Your order has been placed.");
        } else if ("RiderAssigned".equals(event.getEventType())) {
            notificationUseCase.scheduleNotification(event.getAggregateId(), "PUSH", "Rider Assigned", "A rider is on the way.");
        }
    }
}
"""
}

for path, content in files.items():
    with open(path, "w") as f:
        f.write(content)

print("Phase 8 (Notification Bounded Context) scaffolded successfully!")
