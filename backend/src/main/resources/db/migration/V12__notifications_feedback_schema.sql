-- Phases 5, 8: Notification, Feedback

CREATE TABLE IF NOT EXISTS notifications (
    notification_id VARCHAR(255) PRIMARY KEY,
    recipient_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message_body TEXT NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS customer_reviews (
    review_id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    rating INT NOT NULL,
    comment TEXT
);
