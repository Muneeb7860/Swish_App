package ch.swissqcommerce.backend.domain.notification.core.model;

public class Notification {
    private String userId;
    private String message;
    private NotificationType type;

    public Notification(String userId, String message, NotificationType type) {
        this.userId = userId;
        this.message = message;
        this.type = type;
    }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
}
