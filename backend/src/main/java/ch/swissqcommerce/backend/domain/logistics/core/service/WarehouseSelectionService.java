package ch.swissqcommerce.backend.domain.logistics.core.service;

import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.CarrierSlaPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.CarrierSlaPort.CarrierSlaData;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core warehouse-selection service for the RoutingAgent v1.0. Implements the {@link
 * WarehouseSelectionUseCase} inbound port.
 */
@Service
public class WarehouseSelectionService implements WarehouseSelectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(WarehouseSelectionService.class);

    /** Maximum weight (kg) for a single package. Heavier orders become multi-package. */
    static final double SINGLE_PACKAGE_MAX_KG = 30.0;

    private final DarkStoreRepository darkStoreRepo;
    private final InventoryRepository inventoryRepo;
    private final LogisticsDataPort logisticsDataPort;
    private final CarrierSlaPort carrierSlaPort;

    public WarehouseSelectionService(
            DarkStoreRepository darkStoreRepo,
            InventoryRepository inventoryRepo,
            LogisticsDataPort logisticsDataPort,
            CarrierSlaPort carrierSlaPort) {
        this.darkStoreRepo = darkStoreRepo;
        this.inventoryRepo = inventoryRepo;
        this.logisticsDataPort = logisticsDataPort;
        this.carrierSlaPort = carrierSlaPort;
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    @Override
    public Optional<RoutingResult> findOptimalWarehouse(RoutingOrderData order) {
        if (order == null || order.items() == null || order.items().isEmpty()) {
            return Optional.empty();
        }

        List<DarkStore> warehouses =
                darkStoreRepo.findAll().stream()
                        .filter(wh -> Boolean.TRUE.equals(wh.getActive()))
                        .toList();
        if (warehouses.isEmpty()) {
            return Optional.empty();
        }

        CustomerAddress customerAddr = order.customerAddress();
        String zipPrefix = extractZipPrefix(customerAddr);

        // --- Load SLA rules once (cached by Spring) ---
        List<CarrierSlaData> allSlas = loadSlas();

        // --- Derive order-level constraints ---
        double totalWeightKg = computeTotalWeightKg(order.items());
        boolean hasFragile = order.items().stream().anyMatch(item -> item.fragile());
        Integer requestedDays = order.requestedDeliveryDays();

        // --- Async pre-fetch carrier rates for all warehouses (300ms budget) ---
        prefetchCarrierRates(warehouses, zipPrefix);

        // 1. Identify fully-stocked, within-capacity warehouses
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

        // 2. Score and select best warehouse
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

                // Default fallback carrier logic
                String defaultFallback = distance > 50.0 ? "UPS" : "USPS";

                // --- SLA-aware carrier selection ---
                String carrier =
                        selectCarrier(
                                bestWh.getStoreId(),
                                zipPrefix,
                                distance,
                                totalWeightKg,
                                hasFragile,
                                requestedDays,
                                allSlas,
                                order.items(),
                                defaultFallback);

                int estimatedDays = resolveDeliveryDays(carrier, requestedDays, allSlas);
                int packageCount = computePackageCount(totalWeightKg);

                return Optional.of(
                        new RoutingResult(
                                bestWh.getStoreId(),
                                false,
                                bestScore,
                                Collections.emptyList(),
                                carrier,
                                estimatedDays,
                                packageCount));
            }
        }

        // 3. Fallback: split-shipment via region_pref
        Optional<RegionPreference> prefOpt = logisticsDataPort.findRegionPref(zipPrefix);
        if (prefOpt.isPresent()) {
            return buildSplitShipmentResult(
                    prefOpt.get(),
                    order,
                    customerAddr,
                    zipPrefix,
                    totalWeightKg,
                    hasFragile,
                    requestedDays,
                    allSlas);
        }

        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // SLA helpers
    // -------------------------------------------------------------------------

    /**
     * Selects the best carrier for a shipment given SLA constraints. Falls back to a distance-based
     * heuristic if no SLA data is available.
     */
    String selectCarrier(
            String warehouseId,
            String zipPrefix,
            double distanceMiles,
            double totalWeightKg,
            boolean hasFragile,
            Integer requestedDays,
            List<CarrierSlaData> slas,
            List<RoutingOrderData.OrderItem> items,
            String defaultFallback) {

        // Try live carrier rate API first
        Optional<LogisticsDataPort.CarrierRate> liveRate =
                logisticsDataPort.getCarrierRate(warehouseId, zipPrefix);

        String liveCarrier = liveRate.map(LogisticsDataPort.CarrierRate::carrier).orElse(null);

        // Filter eligible carriers using SLA rules
        List<CarrierSlaData> eligible =
                filterEligibleCarriers(slas, totalWeightKg, hasFragile, requestedDays, items);

        if (!eligible.isEmpty()) {
            // Prefer the live API carrier if it passes SLA checks
            if (liveCarrier != null) {
                boolean liveOk =
                        eligible.stream().anyMatch(s -> s.carrier().equalsIgnoreCase(liveCarrier));
                if (liveOk) return liveCarrier;
            }
            // Otherwise pick the first SLA-eligible carrier (already sorted alphabetically)
            return eligible.get(0).carrier();
        }

        // No SLA data or no eligible carrier — fallback to default
        if (liveCarrier != null) return liveCarrier;
        return defaultFallback;
    }

    /**
     * Filters the carrier list to those that satisfy weight, fragile, and delivery-day constraints.
     */
    List<CarrierSlaData> filterEligibleCarriers(
            List<CarrierSlaData> slas,
            double totalWeightKg,
            boolean hasFragile,
            Integer requestedDays,
            List<RoutingOrderData.OrderItem> items) {

        return slas.stream()
                .filter(
                        sla -> {
                            // Check if any individual item exceeds the carrier's max weight limit
                            double maxSingleItemWeight =
                                    items.stream()
                                            .map(
                                                    item ->
                                                            item.weightKg() != null
                                                                    ? item.weightKg().doubleValue()
                                                                    : 0.0)
                                            .max(Double::compare)
                                            .orElse(0.0);
                            if (maxSingleItemWeight > sla.maxWeightKg().doubleValue()) return false;

                            // Weight check: reject if a single package would exceed carrier limit
                            double perPackageWeight =
                                    totalWeightKg <= SINGLE_PACKAGE_MAX_KG
                                            ? totalWeightKg
                                            : SINGLE_PACKAGE_MAX_KG; // heaviest single package
                            if (perPackageWeight > sla.maxWeightKg().doubleValue()) return false;

                            // Fragile check
                            if (hasFragile && !sla.fragileOk()) return false;

                            // Delivery window check
                            if (requestedDays != null) {
                                int bestAvailableDays =
                                        Math.min(sla.standardDays(), sla.expressDays());
                                if (bestAvailableDays > requestedDays) return false;
                            }
                            return true;
                        })
                .toList();
    }

    /** Returns estimated delivery days for a given carrier, using express if needed. */
    int resolveDeliveryDays(String carrier, Integer requestedDays, List<CarrierSlaData> slas) {
        for (CarrierSlaData sla : slas) {
            if (sla.carrier().equalsIgnoreCase(carrier)) {
                if (requestedDays != null && requestedDays <= sla.expressDays()) {
                    return sla.expressDays();
                }
                return sla.standardDays();
            }
        }
        return 5; // default fallback
    }

    /** Calculates the number of packages needed for a given total order weight. */
    int computePackageCount(double totalWeightKg) {
        if (totalWeightKg <= 0) return 1;
        return (int) Math.ceil(totalWeightKg / SINGLE_PACKAGE_MAX_KG);
    }

    /** Loads active SLAs from the port, guarded to never throw. */
    private List<CarrierSlaData> loadSlas() {
        try {
            return carrierSlaPort.findActiveSlas();
        } catch (Exception e) {
            log.warn(
                    "CarrierSlaPort unavailable, proceeding without SLA filtering: {}",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Split-shipment builder
    // -------------------------------------------------------------------------

    private Optional<RoutingResult> buildSplitShipmentResult(
            RegionPreference pref,
            RoutingOrderData order,
            CustomerAddress customerAddr,
            String zipPrefix,
            double totalWeightKg,
            boolean hasFragile,
            Integer requestedDays,
            List<CarrierSlaData> allSlas) {

        boolean primaryHasCapacity = checkCapacity(pref.primaryWarehouseId());
        boolean secondaryHasCapacity =
                pref.secondaryWarehouseId() != null && checkCapacity(pref.secondaryWarehouseId());

        List<String> primaryItems = new ArrayList<>();
        List<String> secondaryItems = new ArrayList<>();
        List<String> unfulfilledItems = new ArrayList<>();

        for (RoutingOrderData.OrderItem item : order.items()) {
            if (hasStock(pref.primaryWarehouseId(), item.itemId(), item.quantity())) {
                primaryItems.add(item.itemId());
            } else if (pref.secondaryWarehouseId() != null
                    && hasStock(pref.secondaryWarehouseId(), item.itemId(), item.quantity())) {
                secondaryItems.add(item.itemId());
            } else {
                unfulfilledItems.add(item.itemId());
            }
        }

        if (!unfulfilledItems.isEmpty()) return Optional.empty();
        if (!primaryItems.isEmpty() && !primaryHasCapacity) return Optional.empty();
        if (!secondaryItems.isEmpty() && !secondaryHasCapacity) return Optional.empty();

        double totalCost = 0.0;
        String carrier = "UPS";
        List<SplitInfo> splits = new ArrayList<>();

        if (!primaryItems.isEmpty()) {
            Optional<DarkStore> wh = darkStoreRepo.findById(pref.primaryWarehouseId());
            double cost =
                    wh.map(store -> scoreWarehouse(store, customerAddr, zipPrefix)).orElse(10.0);
            splits.add(new SplitInfo(pref.primaryWarehouseId(), primaryItems, cost));
            totalCost += cost;
            double dist = wh.map(store -> calculateDistance(customerAddr, store)).orElse(0.0);
            carrier =
                    selectCarrier(
                            pref.primaryWarehouseId(),
                            zipPrefix,
                            dist,
                            totalWeightKg,
                            hasFragile,
                            requestedDays,
                            allSlas,
                            order.items(),
                            "UPS");
        }
        if (!secondaryItems.isEmpty()) {
            Optional<DarkStore> wh = darkStoreRepo.findById(pref.secondaryWarehouseId());
            double cost =
                    wh.map(store -> scoreWarehouse(store, customerAddr, zipPrefix)).orElse(10.0);
            splits.add(new SplitInfo(pref.secondaryWarehouseId(), secondaryItems, cost));
            totalCost += cost;
        }

        int estimatedDays = resolveDeliveryDays(carrier, requestedDays, allSlas);
        int packageCount = computePackageCount(totalWeightKg);

        return Optional.of(
                new RoutingResult(
                        pref.primaryWarehouseId(),
                        true,
                        totalCost,
                        splits,
                        carrier,
                        estimatedDays,
                        packageCount));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean checkCapacity(String warehouseId) {
        Optional<DarkStore> storeOpt = darkStoreRepo.findById(warehouseId);
        if (storeOpt.isEmpty() || !Boolean.TRUE.equals(storeOpt.get().getActive())) return false;
        int capacity =
                storeOpt.get().getDailyOrderCapacity() != null
                        ? storeOpt.get().getDailyOrderCapacity()
                        : 500;
        int todayCount = logisticsDataPort.getTodayOrderCountForWarehouse(warehouseId);
        return todayCount < capacity;
    }

    private double computeTotalWeightKg(List<RoutingOrderData.OrderItem> items) {
        double total = 0.0;
        for (RoutingOrderData.OrderItem item : items) {
            if (item.weightKg() != null) {
                total += item.weightKg().doubleValue() * item.quantity();
            }
        }
        return total;
    }

    private void prefetchCarrierRates(List<DarkStore> warehouses, String zipPrefix) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (DarkStore wh : warehouses) {
            futures.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    logisticsDataPort.getCarrierRate(wh.getStoreId(), zipPrefix);
                                } catch (Exception e) {
                                    /* ignore */
                                }
                            }));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(300, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            /* proceed on timeout */
        }
    }

    private boolean hasAllStock(String storeId, List<RoutingOrderData.OrderItem> items) {
        for (RoutingOrderData.OrderItem item : items) {
            if (!hasStock(storeId, item.itemId(), item.quantity())) return false;
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
            /* fallback */
        }

        double baselineCost = -1.0;
        int sampleSize = 0;

        if (carrierRateOpt.isPresent()) {
            baselineCost = carrierRateOpt.get().rate().doubleValue();
            sampleSize = 10;
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
        if (addr == null || wh == null) return 0.0;
        return calculateHaversineDistance(
                addr.getLatitude(), addr.getLongitude(), wh.getLatitude(), wh.getLongitude());
    }

    private double calculateHaversineDistance(
            BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return 0.0;
        double r = 3958.8;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1.doubleValue()))
                                * Math.cos(Math.toRadians(lat2.doubleValue()))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String extractZipPrefix(CustomerAddress address) {
        if (address == null || address.getAddressLine() == null) return "800";
        String line = address.getAddressLine();
        Matcher m5 = Pattern.compile("\\b\\d{5}\\b").matcher(line);
        if (m5.find()) return m5.group().substring(0, 3);
        Matcher m = Pattern.compile("\\b\\d{3,5}\\b").matcher(line);
        String lastMatch = null;
        while (m.find()) lastMatch = m.group();
        if (lastMatch != null)
            return lastMatch.length() >= 3 ? lastMatch.substring(0, 3) : lastMatch;
        return "800";
    }
}
