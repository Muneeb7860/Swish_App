package ch.swissqcommerce.backend.domain.logistics.core.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port giving the logistics core access to warehouse baseline, region preference, and
 * shipment/order data without depending on adapter persistence classes directly (hexagonal
 * architecture / ADR-001).
 */
public interface LogisticsDataPort {

    /** Baseline cost data for a single warehouse in a given zip-prefix region. */
    record BaselineCost(String warehouseId, BigDecimal avgShippingCost, int sampleSize) {}

    /** Region-level preferred warehouse mapping. */
    record RegionPreference(
            String zipPrefix, String primaryWarehouseId, String secondaryWarehouseId) {}

    /** Shipment cost snapshot for outcome evaluation. */
    record ShipmentCost(Long shipmentId, BigDecimal actualShippingCost) {}

    /** Find all baseline cost entries for the given zip prefix. */
    List<BaselineCost> findBaselinesByZipPrefix(String zipPrefix);

    /** Find region preference (primary/secondary warehouse) by zip prefix. */
    Optional<RegionPreference> findRegionPref(String zipPrefix);

    /** Find all shipment cost entries for a given order ID. */
    List<ShipmentCost> findShipmentCostsByOrderId(Integer orderId);

    /** Find order data required for routing optimization by order ID. */
    Optional<RoutingOrderData> findRoutingOrderData(Integer orderId);

    /** Find the count of orders assigned to a warehouse today (since start of day). */
    int getTodayOrderCountForWarehouse(String warehouseId);

    /** Carrier rate quote wrapper. */
    record CarrierRate(String carrier, java.math.BigDecimal rate) {}

    /** Retrieve the carrier rate quote for a warehouse and destination zip. */
    Optional<CarrierRate> getCarrierRate(String warehouseId, String destinationZip);
}
