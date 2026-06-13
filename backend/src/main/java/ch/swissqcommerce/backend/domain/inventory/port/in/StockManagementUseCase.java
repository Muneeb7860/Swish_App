package ch.swissqcommerce.backend.domain.inventory.port.in;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;

public interface StockManagementUseCase {
    InventoryItem reserveStock(String skuStr, int quantity);

    void releaseStock(String skuStr, int quantity);

    void fulfillStock(String skuStr, int quantity);

    InventoryItem addStock(String skuStr, int quantity);
}
