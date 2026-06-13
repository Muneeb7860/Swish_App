package ch.swissqcommerce.backend.domain.notification.adapter.in.event;

import ch.swissqcommerce.backend.domain.event.core.model.BaseDomainEvent;
import ch.swissqcommerce.backend.domain.notification.port.in.NotificationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationUseCase notificationUseCase;

    // Fully Async Event Listener - no direct coupling!
    @EventListener
    public void handleDomainEvent(BaseDomainEvent event) {
        if ("OrderPlaced".equals(event.getEventType())) {
            notificationUseCase.scheduleNotification(
                    event.getAggregateId(), "EMAIL", "Order Placed", "Your order has been placed.");
        } else if ("RiderAssigned".equals(event.getEventType())) {
            notificationUseCase.scheduleNotification(
                    event.getAggregateId(), "PUSH", "Rider Assigned", "A rider is on the way.");
        }
    }
}
