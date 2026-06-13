package ch.swissqcommerce.backend.domain.notification.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import ch.swissqcommerce.backend.domain.notification.port.out.NotificationPort;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationPort {
    private final NotificationRepository repository;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity =
                NotificationEntity.builder()
                        .notificationId(notification.getNotificationId())
                        .recipientId(notification.getRecipientId())
                        .channel(notification.getChannel())
                        .subject(notification.getSubject())
                        .body(notification.getBody())
                        .status(notification.getStatus())
                        .scheduledAt(notification.getScheduledAt())
                        .sentAt(notification.getSentAt())
                        .build();
        repository.save(entity);
        return notification;
    }

    @Override
    public List<Notification> findPending() {
        return repository.findByStatus("PENDING").stream()
                .map(
                        e ->
                                Notification.builder()
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
        System.out.println(
                "Dispatching "
                        + notification.getChannel()
                        + " to "
                        + notification.getRecipientId());
    }
}
