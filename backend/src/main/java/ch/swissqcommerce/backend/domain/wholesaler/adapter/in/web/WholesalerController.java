package ch.swissqcommerce.backend.domain.wholesaler.adapter.in.web;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wholesaler")
public class WholesalerController {

    @Autowired
    private WholesalerUseCase wholesalerService;

    @Data
    public static class CreateRestockRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;

        private String preferredWholesalerId;
    }

    @Data
    public static class GeneratePoRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;
        @NotBlank(message = "Vendor Name is required")
        private String vendorName;
        private Map<String, Integer> requestedItems;
    }

    @Data
    public static class ReceiveGoodsRequest {
        private Map<String, Integer> itemReceipts;
        private String grnFileUrl;
    }

    @Data
    public static class LogWastageRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;
        @NotBlank(message = "Product ID is required")
        private String productId;
        private String batchId;
        private Integer qty;
        @NotBlank(message = "Reason is required")
        private String reason;
        @NotBlank(message = "Logged By is required")
        private String loggedBy;
    }

    @GetMapping("/restocks")
    public ResponseEntity<List<B2BRestockOrder>> getAssignedRestocks(@RequestParam(required = false, defaultValue = "WHOLESALER-1") String id) {
        List<B2BRestockOrder> restocks = wholesalerService.getAssignedRestocks(id);
        return ResponseEntity.ok(restocks);
    }

    @PostMapping("/restocks")
    public ResponseEntity<B2BRestockOrder> createRestockOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRestockRequest request) {
        B2BRestockOrder order = wholesalerService.createRestockOrder(
                request.getStoreId(), request.getPreferredWholesalerId(), idempotencyKey);
        return ResponseEntity.status(201).body(order);
    }

    @PostMapping("/restocks/{id}/fulfill")
    public ResponseEntity<Map<String, Object>> fulfillRestock(@PathVariable Integer id) {
        Map<String, Object> result = wholesalerService.fulfillRestock(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> getInvoiceSummary(@RequestParam(required = false, defaultValue = "WHOLESALER-1") String id) {
        Map<String, Object> summary = wholesalerService.getInvoiceSummary(id);
        return ResponseEntity.ok(summary);
    }

    // PO & Wastage endpoints
    @PostMapping("/po/generate")
    public ResponseEntity<PurchaseOrder> generateReplenishmentOrders(@Valid @RequestBody GeneratePoRequest request) {
        PurchaseOrder po = wholesalerService.generateReplenishmentOrders(request.getStoreId(), request.getVendorName(), request.getRequestedItems());
        return ResponseEntity.status(201).body(po);
    }

    @PostMapping("/po/{id}/receive")
    public ResponseEntity<PurchaseOrder> receiveGoods(@PathVariable String id, @RequestBody ReceiveGoodsRequest request) {
        PurchaseOrder po = wholesalerService.receiveGoods(id, request.getItemReceipts(), request.getGrnFileUrl());
        return ResponseEntity.ok(po);
    }

    @PostMapping("/wastage")
    public ResponseEntity<WastageLog> logWastage(@Valid @RequestBody LogWastageRequest request) {
        WastageLog log = wholesalerService.logWastage(
                request.getStoreId(), request.getProductId(), request.getBatchId(),
                request.getQty(), request.getReason(), request.getLoggedBy());
        return ResponseEntity.status(201).body(log);
    }
}
