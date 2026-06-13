package ch.swissqcommerce.backend.domain.notification.core.service;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import ch.swissqcommerce.backend.domain.notification.port.in.NotificationUseCase;
import ch.swissqcommerce.backend.domain.notification.port.out.NotificationPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationUseCase {
    private final NotificationPort notificationPort;

    @Override
    public Notification scheduleNotification(
            String recipientId, String channel, String subject, String body) {
        Notification notification =
                Notification.builder()
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
