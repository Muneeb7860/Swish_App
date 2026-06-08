package ch.swissqcommerce.backend.domain.reward.core.model;

import lombok.Value;

@Value
public class Points {
    int amount;

    public Points(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Points cannot be negative");
        }
        this.amount = amount;
    }

    public Points add(int points) {
        return new Points(this.amount + points);
    }

    public Points subtract(int points) {
        if (this.amount < points) {
            throw new IllegalArgumentException("Insufficient points balance");
        }
        return new Points(this.amount - points);
    }
}
