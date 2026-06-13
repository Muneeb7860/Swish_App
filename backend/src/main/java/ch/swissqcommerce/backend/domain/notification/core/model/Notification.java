package ch.swissqcommerce.backend.domain.notification.core.model;

import java.time.OffsetDateTime;

public class Notification {
    private String notificationId;
    private String recipientId;
    private String channel; // SMS, EMAIL, PUSH
    private String subject;
    private String body;
    private String status; // PENDING, SENT, FAILED
    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;

    public Notification() {}

    public Notification(
            String notificationId,
            String recipientId,
            String channel,
            String subject,
            String body,
            String status,
            OffsetDateTime scheduledAt,
            OffsetDateTime sentAt) {
        this.notificationId = notificationId;
        this.recipientId = recipientId;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.sentAt = sentAt;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(OffsetDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String notificationId;
        private String recipientId;
        private String channel;
        private String subject;
        private String body;
        private String status;
        private OffsetDateTime scheduledAt;
        private OffsetDateTime sentAt;

        public Builder notificationId(String notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder recipientId(String recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder scheduledAt(OffsetDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public Builder sentAt(OffsetDateTime sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        public Notification build() {
            return new Notification(
                    notificationId,
                    recipientId,
                    channel,
                    subject,
                    body,
                    status,
                    scheduledAt,
                    sentAt);
        }
    }
}
