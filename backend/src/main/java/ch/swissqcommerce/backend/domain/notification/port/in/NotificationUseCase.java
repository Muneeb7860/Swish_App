package ch.swissqcommerce.backend.domain.notification.port.in;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;

public interface NotificationUseCase {
    Notification scheduleNotification(
            String recipientId, String channel, String subject, String body);

    void sendPendingNotifications();
}
