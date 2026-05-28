package ch.swissqcommerce.backend.domain.notification.adapter.out;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import ch.swissqcommerce.backend.domain.notification.port.out.NotificationProviderPort;
import org.springframework.stereotype.Component;

@Component
public class DummyNotificationProviderAdapter implements NotificationProviderPort {
    @Override
    public void send(Notification notification) {
        System.out.println("Sending " + notification.getType() + " to user " + notification.getUserId() + ": " + notification.getMessage());
    }
}
