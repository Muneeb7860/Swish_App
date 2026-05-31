package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.model.B2BRestockOrder;
import ch.swissqcommerce.backend.service.WholesalerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for B2B Wholesaler operations.
 * Handles restock order management, fulfillment processing, and invoice summaries.
 */
@RestController
@RequestMapping("/api/wholesaler")
@CrossOrigin(origins = {"http://localhost", "http://127.0.0.1"})
public class WholesalerController {

    @Autowired
    private WholesalerService wholesalerService;

    @Data
    public static class CreateRestockRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;

        private String preferredWholesalerId;
    }

    /**
     * GET /api/wholesaler/restocks - Get assigned restock orders.
     */
    @GetMapping("/restocks")
    public ResponseEntity<List<B2BRestockOrder>> getAssignedRestocks(@RequestParam(required = false, defaultValue = "WHOLESALER-1") String id) {
        List<B2BRestockOrder> restocks = wholesalerService.getAssignedRestocks(id);
        return ResponseEntity.ok(restocks);
    }

    /**
     * POST /api/wholesaler/restocks - Create a new restock order.
     */
    @PostMapping("/restocks")
    public ResponseEntity<B2BRestockOrder> createRestockOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRestockRequest request) {
        B2BRestockOrder order = wholesalerService.createRestockOrder(
                request.getStoreId(), request.getPreferredWholesalerId(), idempotencyKey);
        return ResponseEntity.status(201).body(order);
    }

    /**
     * POST /api/wholesaler/restocks/{id}/fulfill - Fulfill a restock order.
     */
    @PostMapping("/restocks/{id}/fulfill")
    public ResponseEntity<Map<String, Object>> fulfillRestock(@PathVariable Integer id) {
        Map<String, Object> result = wholesalerService.fulfillRestock(id);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/wholesaler/invoices - Get invoice summary.
     */
    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> getInvoiceSummary(@RequestParam(required = false, defaultValue = "WHOLESALER-1") String id) {
        Map<String, Object> summary = wholesalerService.getInvoiceSummary(id);
        return ResponseEntity.ok(summary);
    }
}

