package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Inventory and Picker operations.
 * Handles picker queue visibility, cross-store stock rebalancing,
 * and picker-to-rider cargo handover.
 */
@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = {"http://localhost", "http://127.0.0.1"})
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Data
    public static class RebalanceRequest {
        @NotBlank(message = "Item ID is required")
        private String itemId;

        @NotBlank(message = "Source store ID is required")
        private String fromStoreId;

        @NotBlank(message = "Target store ID is required")
        private String toStoreId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }

    @Data
    public static class HandoverRequest {
        @NotNull(message = "Order ID is required")
        private Integer orderId;

        @NotBlank(message = "Picker ID is required")
        private String pickerId;

        @NotBlank(message = "Rider ID is required")
        private String riderId;

        private Integer durationSeconds;
        private Boolean containsPackingError;
    }

    /**
     * GET /api/inventory/picker/queue?storeId={storeId} - Get picker queue for a store.
     */
    @GetMapping("/picker/queue")
    public ResponseEntity<List<Order>> getPickerQueue(@RequestParam String storeId) {
        List<Order> queue = inventoryService.getPickerQueue(storeId);
        return ResponseEntity.ok(queue);
    }

    /**
     * POST /api/inventory/rebalance - Rebalance stock between stores.
     */
    @PostMapping("/rebalance")
    public ResponseEntity<Map<String, Object>> rebalanceStock(@Valid @RequestBody RebalanceRequest request) {
        Map<String, Object> result = inventoryService.rebalanceStock(
                request.getItemId(), request.getFromStoreId(),
                request.getToStoreId(), request.getQuantity());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/inventory/picker/handover - Picker-to-Rider handover.
     */
    @PostMapping("/picker/handover")
    public ResponseEntity<Map<String, Object>> handoverToRider(
            @Valid @RequestBody HandoverRequest request) {
        Map<String, Object> result = inventoryService.handoverToRider(
                request.getOrderId(), request.getPickerId(), request.getRiderId(), request.getDurationSeconds());
        return ResponseEntity.ok(result);
    }
}

