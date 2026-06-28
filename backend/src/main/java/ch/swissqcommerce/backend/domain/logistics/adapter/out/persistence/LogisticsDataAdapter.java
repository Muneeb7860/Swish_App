package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.logistics.adapter.out.carrier.CarrierRateAdapter;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.model.CustomerAddress;
import ch.swissqcommerce.backend.repository.OrderRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Adapter that implements the LogisticsDataPort, delegating to JPA repositories for warehouse
 * baseline, region preference, and shipment data.
 */
@Component
public class LogisticsDataAdapter implements LogisticsDataPort {

    private final WarehouseBaselineRepository baselineRepo;
    private final RegionPrefRepository regionPrefRepo;
    private final ShipmentRepository shipmentRepo;
    private final OrderRepository orderRepo;
    private final CarrierRateAdapter carrierRateAdapter;

    public LogisticsDataAdapter(
            WarehouseBaselineRepository baselineRepo,
            RegionPrefRepository regionPrefRepo,
            ShipmentRepository shipmentRepo,
            OrderRepository orderRepo,
            CarrierRateAdapter carrierRateAdapter) {
        this.baselineRepo = baselineRepo;
        this.regionPrefRepo = regionPrefRepo;
        this.shipmentRepo = shipmentRepo;
        this.orderRepo = orderRepo;
        this.carrierRateAdapter = carrierRateAdapter;
    }

    @Override
    public List<BaselineCost> findBaselinesByZipPrefix(String zipPrefix) {
        return baselineRepo.findByZipPrefix(zipPrefix).stream()
                .map(
                        wb ->
                                new BaselineCost(
                                        wb.getWarehouseId(),
                                        wb.getAvgShippingCost(),
                                        wb.getSampleSize() != null ? wb.getSampleSize() : 0))
                .toList();
    }

    @Override
    public Optional<RegionPreference> findRegionPref(String zipPrefix) {
        return regionPrefRepo
                .findById(zipPrefix)
                .map(
                        rp ->
                                new RegionPreference(
                                        rp.getZipPrefix(),
                                        rp.getPrimaryWarehouseId(),
                                        rp.getSecondaryWarehouseId()));
    }

    @Override
    public List<ShipmentCost> findShipmentCostsByOrderId(Integer orderId) {
        return shipmentRepo.findByOrderOrderId(orderId).stream()
                .map(s -> new ShipmentCost(s.getShipmentId(), s.getActualShippingCost()))
                .toList();
    }

    @Override
    public Optional<RoutingOrderData> findRoutingOrderData(Integer orderId) {
        return orderRepo
                .findById(orderId)
                .map(
                        order -> {
                            CustomerAddress address = null;
                            if (order.getCustomer() != null
                                    && order.getCustomer().getAddresses() != null
                                    && !order.getCustomer().getAddresses().isEmpty()) {
                                address = order.getCustomer().getAddresses().get(0);
                            }

                            List<RoutingOrderData.OrderItem> items =
                                    order.getOrderItems() != null
                                            ? order.getOrderItems().stream()
                                                    .map(
                                                            item ->
                                                                    new RoutingOrderData.OrderItem(
                                                                            item.getItem()
                                                                                    .getItemId(),
                                                                            item.getQuantity()))
                                                    .collect(Collectors.toList())
                                            : List.of();

                            return new RoutingOrderData(
                                    order.getOrderId(), address, order.getStore(), items);
                        });
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LogisticsDataAdapter.class);

    @Override
    public int getTodayOrderCountForWarehouse(String warehouseId) {
        OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        long count =
                orderRepo.countByWarehouseStoreIdAndCreatedAtGreaterThanEqual(
                        warehouseId, startOfDay);
        log.info(
                "getTodayOrderCountForWarehouse: warehouseId={}, startOfDay={}, count={}",
                warehouseId,
                startOfDay,
                count);
        return (int) count;
    }

    @Override
    @Cacheable(value = "carrier-rates", key = "#warehouseId + '-' + #destinationZip")
    public Optional<CarrierRate> getCarrierRate(String warehouseId, String destinationZip) {
        return carrierRateAdapter.getCarrierRate(warehouseId, destinationZip);
    }
}
