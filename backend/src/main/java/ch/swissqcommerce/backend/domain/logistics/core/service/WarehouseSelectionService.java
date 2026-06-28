package ch.swissqcommerce.backend.domain.logistics.core.service;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.BaselineCost;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.RegionPreference;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.model.CustomerAddress;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class WarehouseSelectionService {

    private final DarkStoreRepository darkStoreRepo;
    private final InventoryRepository inventoryRepo;
    private final LogisticsDataPort logisticsDataPort;

    public WarehouseSelectionService(
            DarkStoreRepository darkStoreRepo,
            InventoryRepository inventoryRepo,
            LogisticsDataPort logisticsDataPort) {
        this.darkStoreRepo = darkStoreRepo;
        this.inventoryRepo = inventoryRepo;
        this.logisticsDataPort = logisticsDataPort;
    }

    public static class RoutingResult {
        private final String primaryWarehouseId;
        private final boolean splitShipment;
        private final double estimatedCost;
        private final List<SplitInfo> splits;
        private final String carrier;

        public RoutingResult(
                String primaryWarehouseId,
                boolean splitShipment,
                double estimatedCost,
                List<SplitInfo> splits,
                String carrier) {
            this.primaryWarehouseId = primaryWarehouseId;
            this.splitShipment = splitShipment;
            this.estimatedCost = estimatedCost;
            this.splits = splits;
            this.carrier = carrier;
        }

        public String getPrimaryWarehouseId() {
            return primaryWarehouseId;
        }

        public boolean isSplitShipment() {
            return splitShipment;
        }

        public double getEstimatedCost() {
            return estimatedCost;
        }

        public List<SplitInfo> getSplits() {
            return splits;
        }

        public String getCarrier() {
            return carrier;
        }
    }

    public static class SplitInfo {
        private final String warehouseId;
        private final List<String> itemIds;
        private final double estimatedCost;

        public SplitInfo(String warehouseId, List<String> itemIds, double estimatedCost) {
            this.warehouseId = warehouseId;
            this.itemIds = itemIds;
            this.estimatedCost = estimatedCost;
        }

        public String getWarehouseId() {
            return warehouseId;
        }

        public List<String> getItemIds() {
            return itemIds;
        }

        public double getEstimatedCost() {
            return estimatedCost;
        }
    }

    public Optional<RoutingResult> findOptimalWarehouse(RoutingOrderData order) {
        if (order == null || order.items() == null || order.items().isEmpty()) {
            return Optional.empty();
        }

        List<DarkStore> warehouses = darkStoreRepo.findAll();
        if (warehouses.isEmpty()) {
            return Optional.empty();
        }

        CustomerAddress customerAddr = order.customerAddress();
        String zipPrefix = extractZipPrefix(customerAddr);

        // Async pre-fetch carrier rates for all warehouses in parallel (timeout budget 300ms)
        List<CompletableFuture<Void>> rateFutures = new ArrayList<>();
        for (DarkStore wh : warehouses) {
            rateFutures.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    logisticsDataPort.getCarrierRate(wh.getStoreId(), zipPrefix);
                                } catch (Exception e) {
                                    // ignore
                                }
                            }));
        }

        try {
            CompletableFuture.allOf(rateFutures.toArray(new CompletableFuture[0]))
                    .get(300, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Proceed on timeout/exception and use fallback
        }

        // 1. Identify which warehouses have 100% SKU availability AND capacity remaining
        List<DarkStore> fullyStockedWarehouses = new ArrayList<>();
        for (DarkStore wh : warehouses) {
            if (hasAllStock(wh.getStoreId(), order.items())) {
                int capacity =
                        wh.getDailyOrderCapacity() != null ? wh.getDailyOrderCapacity() : 500;
                int todayCount = logisticsDataPort.getTodayOrderCountForWarehouse(wh.getStoreId());
                if (todayCount < capacity) {
                    fullyStockedWarehouses.add(wh);
                }
            }
        }

        // 2. Score candidate warehouses
        if (!fullyStockedWarehouses.isEmpty()) {
            DarkStore bestWh = null;
            double bestScore = Double.MAX_VALUE;

            for (DarkStore wh : fullyStockedWarehouses) {
                double score = scoreWarehouse(wh, customerAddr, zipPrefix);
                if (score < bestScore) {
                    bestScore = score;
                    bestWh = wh;
                }
            }

            if (bestWh != null) {
                double distance = calculateDistance(customerAddr, bestWh);
                String carrier = "USPS";
                Optional<LogisticsDataPort.CarrierRate> carrierRateOpt =
                        logisticsDataPort.getCarrierRate(bestWh.getStoreId(), zipPrefix);
                if (carrierRateOpt.isPresent()) {
                    carrier = carrierRateOpt.get().carrier();
                } else {
                    carrier = distance > 50.0 ? "UPS" : "USPS";
                }
                return Optional.of(
                        new RoutingResult(
                                bestWh.getStoreId(),
                                false,
                                bestScore,
                                Collections.emptyList(),
                                carrier));
            }
        }

        // 3. Fallback: Split-shipment lookup from region_pref via port
        Optional<RegionPreference> prefOpt = logisticsDataPort.findRegionPref(zipPrefix);
        if (prefOpt.isPresent()) {
            RegionPreference pref = prefOpt.get();

            boolean primaryHasCapacity = true;
            Optional<DarkStore> primaryStoreOpt = darkStoreRepo.findById(pref.primaryWarehouseId());
            if (primaryStoreOpt.isPresent()) {
                int capacity =
                        primaryStoreOpt.get().getDailyOrderCapacity() != null
                                ? primaryStoreOpt.get().getDailyOrderCapacity()
                                : 500;
                int todayCount =
                        logisticsDataPort.getTodayOrderCountForWarehouse(pref.primaryWarehouseId());
                if (todayCount >= capacity) {
                    primaryHasCapacity = false;
                }
            } else {
                primaryHasCapacity = false;
            }

            boolean secondaryHasCapacity = true;
            if (pref.secondaryWarehouseId() != null) {
                Optional<DarkStore> secondaryStoreOpt =
                        darkStoreRepo.findById(pref.secondaryWarehouseId());
                if (secondaryStoreOpt.isPresent()) {
                    int capacity =
                            secondaryStoreOpt.get().getDailyOrderCapacity() != null
                                    ? secondaryStoreOpt.get().getDailyOrderCapacity()
                                    : 500;
                    int todayCount =
                            logisticsDataPort.getTodayOrderCountForWarehouse(
                                    pref.secondaryWarehouseId());
                    if (todayCount >= capacity) {
                        secondaryHasCapacity = false;
                    }
                } else {
                    secondaryHasCapacity = false;
                }
            }

            List<SplitInfo> splits = new ArrayList<>();
            List<String> unfulfilledItems = new ArrayList<>();

            List<String> primaryItems = new ArrayList<>();
            List<String> secondaryItems = new ArrayList<>();

            for (RoutingOrderData.OrderItem item : order.items()) {
                String itemId = item.itemId();
                int qtyNeeded = item.quantity();

                if (hasStock(pref.primaryWarehouseId(), itemId, qtyNeeded)) {
                    primaryItems.add(itemId);
                } else if (pref.secondaryWarehouseId() != null
                        && hasStock(pref.secondaryWarehouseId(), itemId, qtyNeeded)) {
                    secondaryItems.add(itemId);
                } else {
                    unfulfilledItems.add(itemId);
                }
            }

            if (!unfulfilledItems.isEmpty()) {
                return Optional.empty();
            }

            // Exceeding capacity fails auto-routing -> goes to HITL
            if (!primaryItems.isEmpty() && !primaryHasCapacity) {
                return Optional.empty();
            }
            if (!secondaryItems.isEmpty() && !secondaryHasCapacity) {
                return Optional.empty();
            }

            double totalCost = 0.0;
            String carrier = "UPS";
            if (!primaryItems.isEmpty()) {
                Optional<DarkStore> wh = darkStoreRepo.findById(pref.primaryWarehouseId());
                double cost =
                        wh.map(store -> scoreWarehouse(store, customerAddr, zipPrefix))
                                .orElse(10.0);
                splits.add(new SplitInfo(pref.primaryWarehouseId(), primaryItems, cost));
                totalCost += cost;

                Optional<LogisticsDataPort.CarrierRate> cr =
                        logisticsDataPort.getCarrierRate(pref.primaryWarehouseId(), zipPrefix);
                if (cr.isPresent()) {
                    carrier = cr.get().carrier();
                }
            }
            if (!secondaryItems.isEmpty()) {
                Optional<DarkStore> wh = darkStoreRepo.findById(pref.secondaryWarehouseId());
                double cost =
                        wh.map(store -> scoreWarehouse(store, customerAddr, zipPrefix))
                                .orElse(10.0);
                splits.add(new SplitInfo(pref.secondaryWarehouseId(), secondaryItems, cost));
                totalCost += cost;
            }

            return Optional.of(
                    new RoutingResult(pref.primaryWarehouseId(), true, totalCost, splits, carrier));
        }

        return Optional.empty();
    }

    private boolean hasAllStock(String storeId, List<RoutingOrderData.OrderItem> items) {
        for (RoutingOrderData.OrderItem item : items) {
            if (!hasStock(storeId, item.itemId(), item.quantity())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStock(String storeId, String itemId, int qtyNeeded) {
        List<Inventory> storeInventory = inventoryRepo.findByStoreStoreId(storeId);
        for (Inventory inv : storeInventory) {
            if (inv.getItemId().equals(itemId)) {
                int available =
                        inv.getStock() - (inv.getReservedQty() != null ? inv.getReservedQty() : 0);
                return available >= qtyNeeded;
            }
        }
        return false;
    }

    private double scoreWarehouse(DarkStore wh, CustomerAddress addr, String zipPrefix) {
        Optional<LogisticsDataPort.CarrierRate> carrierRateOpt = Optional.empty();
        try {
            carrierRateOpt = logisticsDataPort.getCarrierRate(wh.getStoreId(), zipPrefix);
        } catch (Exception e) {
            // fallback
        }

        double baselineCost = -1.0;
        int sampleSize = 0;

        if (carrierRateOpt.isPresent()) {
            baselineCost = carrierRateOpt.get().rate().doubleValue();
            sampleSize = 10; // High confidence live rate
        } else {
            List<BaselineCost> baselines = logisticsDataPort.findBaselinesByZipPrefix(zipPrefix);
            for (BaselineCost bc : baselines) {
                if (bc.warehouseId().equals(wh.getStoreId())) {
                    baselineCost = bc.avgShippingCost().doubleValue();
                    sampleSize = bc.sampleSize();
                    break;
                }
            }
        }

        double distance = calculateDistance(addr, wh);
        if (baselineCost < 0.0) {
            baselineCost = 2.00 + (distance * 0.10);
        } else if (sampleSize < 5) {
            baselineCost *= 1.20;
        }

        return baselineCost + (distance * 0.10);
    }

    public double calculateDistance(CustomerAddress addr, DarkStore wh) {
        if (addr == null || wh == null) {
            return 0.0;
        }
        return calculateHaversineDistance(
                addr.getLatitude(), addr.getLongitude(), wh.getLatitude(), wh.getLongitude());
    }

    private double calculateHaversineDistance(
            BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0.0;
        }
        double r = 3958.8;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1.doubleValue()))
                                * Math.cos(Math.toRadians(lat2.doubleValue()))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private String extractZipPrefix(CustomerAddress address) {
        if (address == null || address.getAddressLine() == null) {
            return "800";
        }
        String line = address.getAddressLine();
        Matcher m5 = Pattern.compile("\\b\\d{5}\\b").matcher(line);
        if (m5.find()) {
            return m5.group().substring(0, 3);
        }
        Matcher m = Pattern.compile("\\b\\d{3,5}\\b").matcher(line);
        String lastMatch = null;
        while (m.find()) {
            lastMatch = m.group();
        }
        if (lastMatch != null) {
            return lastMatch.length() >= 3 ? lastMatch.substring(0, 3) : lastMatch;
        }
        return "800";
    }
}
