package ch.swissqcommerce.backend.domain.logistics.core.port.in;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port for triggering warehouse routing and carrier selection decisions. Decouples Web/REST
 * adapters from core service implementations (ADR-001).
 */
public interface WarehouseSelectionUseCase {

    /** Detailed result of a warehouse routing and carrier selection decision. */
    record RoutingResult(
            String primaryWarehouseId,
            boolean splitShipment,
            double estimatedCost,
            List<SplitInfo> splits,
            String carrier,
            int estimatedDeliveryDays,
            int packageCount) {}

    /** Details of a single package split in a split-shipment scenario. */
    record SplitInfo(String warehouseId, List<String> itemIds, double estimatedCost) {}

    /**
     * Finds the optimal warehouse(s) and carrier for the given order data.
     *
     * @param order the order data to route
     * @return the routing result, or empty if routing fails (e.g. inventory/capacity issues)
     */
    Optional<RoutingResult> findOptimalWarehouse(RoutingOrderData order);
}
