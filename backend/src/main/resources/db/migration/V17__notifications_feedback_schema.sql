-- Phase 5/8: Notification, Feedback (renumbered from V12).
-- oltp.feedbacks already exists from earlier migrations; this adds the new
-- customer_reviews (post-order star ratings + free-text) and notifications
-- tables.

CREATE TABLE IF NOT EXISTS oltp.notifications (
    notification_id  VARCHAR(50)  PRIMARY KEY,
    recipient_id     VARCHAR(50)  NOT NULL,
    channel          VARCHAR(20)  NOT NULL,
    subject          VARCHAR(255),
    body             TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    scheduled_at     TIMESTAMP WITH TIME ZONE,
    sent_at          TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_channel_chk CHECK (channel IN ('EMAIL', 'SMS', 'PUSH', 'WEBHOOK')),
    CONSTRAINT notifications_status_chk  CHECK (status  IN ('QUEUED', 'SENT', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON oltp.notifications (recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_status    ON oltp.notifications (status, created_at);

CREATE TABLE IF NOT EXISTS oltp.customer_reviews (
    review_id     VARCHAR(50) PRIMARY KEY,
    order_id      VARCHAR(50) NOT NULL,
    customer_id   VARCHAR(50) NOT NULL,
    rating        INTEGER     NOT NULL,
    comment       TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_reviews_rating_chk CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT customer_reviews_order_customer_unique UNIQUE (order_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_reviews_order    ON oltp.customer_reviews (order_id);
CREATE INDEX IF NOT EXISTS idx_customer_reviews_customer ON oltp.customer_reviews (customer_id);
