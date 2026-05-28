package ch.swissqcommerce.backend.domain.notification.port.in;

import ch.swissqcommerce.backend.domain.notification.core.model.NotificationType;

public interface NotificationUseCase {
    void sendNotification(String userId, String message, NotificationType type);
}
