package ch.swissqcommerce.backend.domain.notification.port.out;

import ch.swissqcommerce.backend.domain.notification.core.model.Notification;
import java.util.List;

public interface NotificationPort {
    Notification save(Notification notification);
    List<Notification> findPending();
    void dispatch(Notification notification);
}
