package ch.swissqcommerce.backend.domain.inventory.core.model;

import lombok.Value;

@Value
public class Quantity {
    int value;

    public Quantity(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        this.value = value;
    }

    public Quantity add(int amount) {
        return new Quantity(this.value + amount);
    }

    public Quantity subtract(int amount) {
        if (this.value < amount) {
            throw new IllegalArgumentException("Insufficient quantity");
        }
        return new Quantity(this.value - amount);
    }
}
