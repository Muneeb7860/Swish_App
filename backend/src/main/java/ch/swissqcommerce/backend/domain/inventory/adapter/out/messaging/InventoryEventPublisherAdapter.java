package ch.swissqcommerce.backend.domain.inventory.adapter.out.messaging;

import ch.swissqcommerce.backend.domain.inventory.port.out.InventoryEventPublisherPort;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventPublisherAdapter implements InventoryEventPublisherPort {

    @Override
    public void publishStockReservedEvent(String sku, int quantity) {
        // Dummy implementation
    }

    @Override
    public void publishLowStockEvent(String sku, int currentAvailable) {
        // Dummy implementation
    }
}
