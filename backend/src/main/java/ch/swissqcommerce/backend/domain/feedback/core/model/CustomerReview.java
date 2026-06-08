package ch.swissqcommerce.backend.domain.feedback.core.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerReview {
    private final String reviewId;
    private final String orderId;
    private final String customerId;
    private Rating rating;
    private Comment comment;
    private boolean isFlagged;
    private boolean isPublished;

    public void publish() {
        if (this.isFlagged) {
            throw new IllegalStateException("Cannot publish a flagged review");
        }
        this.isPublished = true;
    }

    public void flagForReview() {
        this.isFlagged = true;
        this.isPublished = false;
    }
}
