package ch.swissqcommerce.backend.domain.inventory.adapter.in.web;

import ch.swissqcommerce.backend.model.Picker;
import ch.swissqcommerce.backend.repository.PickerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory & Picker", description = "Picker queue management, order handover, and stock rebalancing")
public class PickerController {

    private final PickerRepository pickerRepository;

    @Data
    public static class HandoverRequest {
        @NotBlank private String pickerId;
        @NotNull  private Integer orderId;
        private String notes;
    }

    @Data
    public static class RebalanceRequest {
        @NotBlank private String fromStoreId;
        @NotBlank private String toStoreId;
        @NotBlank private String sku;
        @NotNull  private Integer quantity;
    }

    /**
     * GET /api/inventory/picker/queue — list pickers currently active in store.
     */
    @Operation(summary = "List picker queue",
               description = "Returns all pickers currently active and available for order picking in a given store.")
    @GetMapping("/picker/queue")
    public ResponseEntity<List<Picker>> getPickerQueue(
            @RequestParam(required = false) String storeId) {
        List<Picker> pickers = storeId != null
                ? pickerRepository.findByActiveStoreStoreId(storeId)
                : pickerRepository.findAll();
        return ResponseEntity.ok(pickers);
    }

    /**
     * POST /api/inventory/picker/handover — picker hands over packed order to rider.
     */
    @Operation(summary = "Picker handover to rider",
               description = "Records that a picker has completed picking and handed the packed order to the assigned rider.")
    @PostMapping("/picker/handover")
    public ResponseEntity<Map<String, Object>> handover(@Valid @RequestBody HandoverRequest req) {
        // In full implementation this calls a PickerUseCase.handover() method.
        return ResponseEntity.ok(Map.of(
                "pickerId", req.getPickerId(),
                "orderId", req.getOrderId(),
                "status", "HANDED_OVER",
                "message", "Order successfully handed over to rider."
        ));
    }

    /**
     * POST /api/inventory/rebalance — trigger inter-store stock rebalancing.
     */
    @Operation(summary = "Trigger stock rebalancing",
               description = "Initiates a transfer of SKU stock between dark-store locations to prevent stockouts.")
    @PostMapping("/rebalance")
    public ResponseEntity<Map<String, Object>> rebalance(@Valid @RequestBody RebalanceRequest req) {
        // Delegates to a StockRebalanceUseCase in full implementation.
        return ResponseEntity.ok(Map.of(
                "fromStoreId", req.getFromStoreId(),
                "toStoreId", req.getToStoreId(),
                "sku", req.getSku(),
                "quantity", req.getQuantity(),
                "status", "REBALANCE_QUEUED",
                "message", "Rebalance task queued successfully."
        ));
    }
}
