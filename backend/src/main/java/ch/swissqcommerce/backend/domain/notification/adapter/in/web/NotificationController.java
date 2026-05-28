package ch.swissqcommerce.backend.domain.notification.adapter.in.web;

import ch.swissqcommerce.backend.domain.notification.core.model.NotificationType;
import ch.swissqcommerce.backend.domain.notification.port.in.NotificationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationUseCase notificationUseCase;

    public NotificationController(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @PostMapping("/send")
    public ResponseEntity<Void> sendNotification(@RequestParam String userId, @RequestParam String message, @RequestParam String type) {
        try {
            NotificationType notificationType = NotificationType.valueOf(type.toUpperCase());
            notificationUseCase.sendNotification(userId, message, notificationType);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
