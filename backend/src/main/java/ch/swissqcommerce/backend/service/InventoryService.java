package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages inventory operations across dark stores.
 * Handles picker queue assignment, cross-store rebalancing,
 * and picker-to-rider cargo handover processes.
 */
@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PickerRepository pickerRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private SecurityTrustLedgerRepository trustLedgerRepository;

    /**
     * Returns orders currently pending picker assignment at a given store.
     * Supports the F09 picker queue bottleneck visibility feature.
     */
    public List<Order> getPickerQueue(String storeId) {
        // Return pending orders for the given store, sorted oldest first
        return orderRepository.findAll().stream()
                .filter(o -> "pending".equalsIgnoreCase(o.getStatus()))
                .filter(o -> o.getStore() != null && storeId.equals(o.getStore().getStoreId()))
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Rebalances stock between dark stores.
     * Transfers units of a product from the source store to the target store.
     * Uses SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> rebalanceStock(String itemId, String fromStoreId, String toStoreId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Rebalance quantity must be positive.");
        }
        if (fromStoreId.equals(toStoreId)) {
            throw new IllegalArgumentException("Source and target store cannot be the same.");
        }

        // Find inventory records at both stores
        List<Inventory> sourceItems = inventoryRepository.findByStoreStoreId(fromStoreId).stream()
                .filter(i -> i.getItemId().equals(itemId))
                .collect(Collectors.toList());

        if (sourceItems.isEmpty()) {
            throw new NoSuchElementException("Item " + itemId + " not found at source store " + fromStoreId);
        }
        Inventory source = sourceItems.get(0);
        if (source.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock at source store. Available: " + source.getStock());
        }

        List<Inventory> targetItems = inventoryRepository.findByStoreStoreId(toStoreId).stream()
                .filter(i -> i.getName().equalsIgnoreCase(source.getName()))
                .collect(Collectors.toList());

        if (targetItems.isEmpty()) {
            throw new NoSuchElementException("Matching product not found at target store " + toStoreId);
        }
        Inventory target = targetItems.get(0);

        // Execute transfer
        source.setStock(source.getStock() - quantity);
        target.setStock(target.getStock() + quantity);
        inventoryRepository.save(source);
        inventoryRepository.save(target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "rebalanced");
        result.put("item", source.getName());
        result.put("quantity", quantity);
        result.put("fromStore", fromStoreId);
        result.put("fromStockAfter", source.getStock());
        result.put("toStore", toStoreId);
        result.put("toStockAfter", target.getStock());
        return result;
    }

    /**
     * Picker-to-Rider handover. Transitions order from 'picking' to 'shipping'.
     * Awards Lightning Badge to picker if completed in under 90 seconds.
     * Validates picker and rider trust scores before allowing handover.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> handoverToRider(Integer orderId, String pickerId, String riderId, Integer pickTimeSec) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (!"pending".equalsIgnoreCase(order.getStatus()) && !"picking".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Order is not in a handover-ready state. Current: " + order.getStatus());
        }

        Picker picker = pickerRepository.findById(pickerId)
                .orElseThrow(() -> new NoSuchElementException("Picker not found: " + pickerId));

        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));

        // Validate trust thresholds
        if (picker.getTrustScore() < 50) {
            throw new IllegalStateException("Picker trust score too low for handover: " + picker.getTrustScore());
        }
        if (rider.getTrustScore() < 50) {
            throw new IllegalStateException("Rider trust score too low for handover: " + rider.getTrustScore());
        }

        // Transition order state
        order.setStatus("shipping");
        order.setRider(rider);
        orderRepository.save(order);

        // F10: Lightning Badge award for sub-90s pick times
        boolean lightningAwarded = false;
        if (pickTimeSec != null && pickTimeSec < 90 && !picker.getLightningBadge()) {
            picker.setLightningBadge(true);
            pickerRepository.save(picker);
            lightningAwarded = true;

            // Audit the badge award
            SecurityTrustLedger audit = SecurityTrustLedger.builder()
                    .actorType("picker")
                    .actorId(pickerId)
                    .event("LIGHTNING-BADGE-AWARDED")
                    .delta(0)
                    .currentValue(picker.getTrustScore())
                    .build();
            trustLedgerRepository.save(audit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "handed_over");
        result.put("orderId", orderId);
        result.put("pickerId", pickerId);
        result.put("riderId", riderId);
        result.put("orderStatus", "shipping");
        result.put("lightningBadgeAwarded", lightningAwarded);
        if (pickTimeSec != null) {
            result.put("pickTimeSec", pickTimeSec);
        }
        return result;
    }
}

