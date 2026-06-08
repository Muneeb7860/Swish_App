package ch.swissqcommerce.backend.domain.inventory.core.model;

import lombok.Value;

@Value
public class SKU {
    String value;

    public SKU(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be empty");
        }
        this.value = value;
    }
}
