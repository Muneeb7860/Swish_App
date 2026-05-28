package ch.swissqcommerce.backend.domain.notification.port.out;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;

public interface NotificationProviderPort {
    void send(Notification notification);
}
