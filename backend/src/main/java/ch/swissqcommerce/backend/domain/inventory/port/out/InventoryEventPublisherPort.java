package ch.swissqcommerce.backend.domain.inventory.port.out;

public interface InventoryEventPublisherPort {
    void publishStockReservedEvent(String sku, int quantity);

    void publishLowStockEvent(String sku, int currentAvailable);
}
