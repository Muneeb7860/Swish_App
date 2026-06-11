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
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class B2BInvoiceDto {
        @JsonProperty("invoice_id")
        private Integer invoiceId;
        @JsonProperty("restock_order_id")
        private Integer restockOrderId;
        @JsonProperty("wholesaler_id")
        private String wholesalerId;
        private BigDecimal amount;
        private String status;
        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<B2BInvoiceDto>> getInvoices(@RequestParam(required = false, defaultValue = "WHOLESALER-1") String id) {
        List<B2BRestockOrder> restocks = wholesalerService.getAssignedRestocks(id);
        List<B2BInvoiceDto> invoices = restocks.stream()
                .map(order -> new B2BInvoiceDto(
                        order.getRestockOrderId(), // invoice_id
                        order.getRestockOrderId(), // restock_order_id
                        order.getWholesaler() != null ? order.getWholesaler().getWholesalerId() : id,
                        order.getInvoiceAmount(),
                        "fulfilled".equalsIgnoreCase(order.getStatus()) ? "paid" : "unpaid",
                        order.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(invoices);
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
