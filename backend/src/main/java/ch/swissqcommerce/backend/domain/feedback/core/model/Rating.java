package ch.swissqcommerce.backend.domain.feedback.core.model;

import lombok.Value;

@Value
public class Rating {
    int stars;

    public Rating(int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5 stars");
        }
        this.stars = stars;
    }
}
