package ch.swissqcommerce.backend.domain.inventory.core.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryItem {
    private final String id;
    private SKU sku;
    private Quantity availableQuantity;
    private Quantity reservedQuantity;

    public void reserve(int amount) {
        this.availableQuantity = this.availableQuantity.subtract(amount);
        this.reservedQuantity = this.reservedQuantity.add(amount);
    }

    public void release(int amount) {
        this.reservedQuantity = this.reservedQuantity.subtract(amount);
        this.availableQuantity = this.availableQuantity.add(amount);
    }

    public void fulfill(int amount) {
        this.reservedQuantity = this.reservedQuantity.subtract(amount);
    }

    public void addStock(int amount) {
        this.availableQuantity = this.availableQuantity.add(amount);
    }
}
