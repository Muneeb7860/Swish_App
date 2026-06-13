package ch.swissqcommerce.backend.domain.inventory.core.service;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.core.model.SKU;
import ch.swissqcommerce.backend.domain.inventory.port.in.StockManagementUseCase;
import ch.swissqcommerce.backend.domain.inventory.port.out.InventoryEventPublisherPort;
import ch.swissqcommerce.backend.domain.inventory.port.out.InventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements StockManagementUseCase {

    private final InventoryRepositoryPort inventoryRepositoryPort;
    private final InventoryEventPublisherPort eventPublisherPort;

    private static void requirePositiveQuantity(int quantity, String operation) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    operation + " quantity must be greater than zero, got: " + quantity);
        }
    }

    @Override
    @Transactional
    public InventoryItem reserveStock(String skuStr, int quantity) {
        requirePositiveQuantity(quantity, "Reserve");
        SKU sku = new SKU(skuStr);
        InventoryItem item =
                inventoryRepositoryPort
                        .findBySku(sku)
                        .orElseThrow(() -> new IllegalArgumentException("SKU not found"));

        item.reserve(quantity);
        InventoryItem savedItem = inventoryRepositoryPort.save(item);

        eventPublisherPort.publishStockReservedEvent(skuStr, quantity);

        if (savedItem.getAvailableQuantity().getValue() < 10) { // Low stock threshold
            eventPublisherPort.publishLowStockEvent(
                    skuStr, savedItem.getAvailableQuantity().getValue());
        }

        return savedItem;
    }

    @Override
    @Transactional
    public void releaseStock(String skuStr, int quantity) {
        requirePositiveQuantity(quantity, "Release");
        SKU sku = new SKU(skuStr);
        InventoryItem item =
                inventoryRepositoryPort
                        .findBySku(sku)
                        .orElseThrow(() -> new IllegalArgumentException("SKU not found"));

        item.release(quantity);
        inventoryRepositoryPort.save(item);
    }

    @Override
    @Transactional
    public void fulfillStock(String skuStr, int quantity) {
        requirePositiveQuantity(quantity, "Fulfill");
        SKU sku = new SKU(skuStr);
        InventoryItem item =
                inventoryRepositoryPort
                        .findBySku(sku)
                        .orElseThrow(() -> new IllegalArgumentException("SKU not found"));

        item.fulfill(quantity);
        inventoryRepositoryPort.save(item);
    }

    @Override
    @Transactional
    public InventoryItem addStock(String skuStr, int quantity) {
        requirePositiveQuantity(quantity, "AddStock");
        SKU sku = new SKU(skuStr);
        InventoryItem item =
                inventoryRepositoryPort
                        .findBySku(sku)
                        .orElseThrow(() -> new IllegalArgumentException("SKU not found"));

        item.addStock(quantity);
        return inventoryRepositoryPort.save(item);
    }
}
