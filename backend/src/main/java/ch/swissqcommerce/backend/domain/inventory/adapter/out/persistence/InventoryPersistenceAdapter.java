package ch.swissqcommerce.backend.domain.inventory.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.core.model.Quantity;
import ch.swissqcommerce.backend.domain.inventory.core.model.SKU;
import ch.swissqcommerce.backend.domain.inventory.port.out.InventoryRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA-backed implementation of InventoryRepositoryPort. Replaces the previous no-op stub that
 * silently discarded all writes.
 */
@Component
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private final InventoryItemRepository repository;

    @Override
    public InventoryItem save(InventoryItem item) {
        InventoryItemEntity entity =
                repository
                        .findBySku(item.getSku().getValue())
                        .orElseGet(
                                () ->
                                        InventoryItemEntity.builder()
                                                .id(
                                                        item.getId() != null
                                                                ? item.getId()
                                                                : UUID.randomUUID().toString())
                                                .sku(item.getSku().getValue())
                                                .availableAmount(0)
                                                .reservedAmount(0)
                                                .build());

        entity.setAvailableAmount(item.getAvailableQuantity().getValue());
        entity.setReservedAmount(item.getReservedQuantity().getValue());

        InventoryItemEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<InventoryItem> findBySku(SKU sku) {
        return repository.findBySku(sku.getValue()).map(this::toDomain);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private InventoryItem toDomain(InventoryItemEntity entity) {
        return InventoryItem.builder()
                .id(entity.getId())
                .sku(new SKU(entity.getSku()))
                .availableQuantity(new Quantity(entity.getAvailableAmount()))
                .reservedQuantity(new Quantity(entity.getReservedAmount()))
                .build();
    }
}
