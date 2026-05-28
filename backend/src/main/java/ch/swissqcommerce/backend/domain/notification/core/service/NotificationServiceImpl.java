package ch.swissqcommerce.backend.domain.notification.core.service;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import ch.swissqcommerce.backend.domain.notification.core.model.NotificationType;
import ch.swissqcommerce.backend.domain.notification.port.in.NotificationUseCase;
import ch.swissqcommerce.backend.domain.notification.port.out.NotificationProviderPort;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationUseCase {

    private final NotificationProviderPort notificationProviderPort;

    public NotificationServiceImpl(NotificationProviderPort notificationProviderPort) {
        this.notificationProviderPort = notificationProviderPort;
    }

    @Override
    public void sendNotification(String userId, String message, NotificationType type) {
        Notification notification = new Notification(userId, message, type);
        notificationProviderPort.send(notification);
    }
}
