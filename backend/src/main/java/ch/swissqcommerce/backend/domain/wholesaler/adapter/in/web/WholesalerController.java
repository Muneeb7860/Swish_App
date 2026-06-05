package ch.swissqcommerce.backend.domain.wholesaler.adapter.in.web;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
