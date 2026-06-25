package ch.swissqcommerce.backend.domain.logistics.core.service;

import ch.swissqcommerce.backend.domain.governance.core.service.OutcomeProcessor;
import ch.swissqcommerce.backend.domain.governance.core.service.OutcomeResult;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.BaselineCost;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.ShipmentCost;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogisticsOutcomeProcessor implements OutcomeProcessor {

    private static final Logger log = LoggerFactory.getLogger(LogisticsOutcomeProcessor.class);

    private final LogisticsDataPort logisticsDataPort;

    public LogisticsOutcomeProcessor(LogisticsDataPort logisticsDataPort) {
        this.logisticsDataPort = logisticsDataPort;
    }

    @Override
    public String domain() {
        return "routing";
    }

    @Override
    public OutcomeResult evaluate(ExecutionRecord exec, AgentSuggestionEntity suggestion)
            throws Exception {
        String entityId = suggestion.getEntityId();
        String orderIdStr = entityId;
        if (entityId != null && entityId.contains("=")) {
            orderIdStr = entityId.split("=")[1];
        }

        Integer orderId = null;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            log.warn("LogisticsOutcomeProcessor: Invalid order ID format: {}", entityId);
            return emptyResult("invalid_order_id");
        }

        Optional<RoutingOrderData> orderOpt = logisticsDataPort.findRoutingOrderData(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("LogisticsOutcomeProcessor: Order not found: {}", orderId);
            return emptyResult("order_not_found");
        }
        RoutingOrderData order = orderOpt.get();

        // 1. Calculate baseline cost
        CustomerAddress customerAddr = order.customerAddress();
        String zipPrefix = extractZipPrefix(customerAddr);
        DarkStore originalStore = order.store();

        double baselineCost = calculateScoredCost(originalStore, customerAddr, zipPrefix);

        // 2. Fetch actual shipping cost from shipments via port
        List<ShipmentCost> shipments =
                logisticsDataPort.findShipmentCostsByOrderId(order.orderId());
        if (shipments.isEmpty()) {
            log.warn("LogisticsOutcomeProcessor: No shipments found for order {}", orderId);
            return emptyResult("no_shipments_found");
        }

        BigDecimal totalActual = BigDecimal.ZERO;
        boolean allSettled = true;
        for (ShipmentCost s : shipments) {
            if (s.actualShippingCost() == null) {
                allSettled = false;
                break;
            }
            totalActual = totalActual.add(s.actualShippingCost());
        }

        BigDecimal baselineCostBd =
                BigDecimal.valueOf(baselineCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal savings;
        boolean success;

        if (!allSettled) {
            log.info(
                    "LogisticsOutcomeProcessor: Not all shipments settled for order {}. Savings set"
                            + " to 0.",
                    orderId);
            savings = BigDecimal.ZERO;
            success = false;
        } else {
            savings = baselineCostBd.subtract(totalActual);
            success = savings.compareTo(BigDecimal.ZERO) > 0;
        }

        Map<String, Object> metrics = Map.of("shipping_savings_usd", savings.doubleValue());

        OffsetDateTime start = exec.getCreatedAt();
        OffsetDateTime end = start.plusDays(3);

        return OutcomeResult.builder()
                .metrics(metrics)
                .success(success)
                .measurementWindow(String.format("[%s, %s)", start, end))
                .notes(
                        String.format(
                                "Logistics assessment: baseline = %.2f, actual = %s, savings ="
                                        + " %.2f",
                                baselineCostBd,
                                allSettled ? totalActual.toString() : "null",
                                savings))
                .build();
    }

    private OutcomeResult emptyResult(String reason) {
        return OutcomeResult.builder()
                .metrics(Map.of("shipping_savings_usd", 0.0))
                .success(false)
                .measurementWindow("")
                .notes(reason)
                .build();
    }

    private double calculateScoredCost(DarkStore wh, CustomerAddress addr, String zipPrefix) {
        if (wh == null) {
            return 10.0;
        }
        List<BaselineCost> baselines = logisticsDataPort.findBaselinesByZipPrefix(zipPrefix);
        double baselineCost = -1.0;
        int sampleSize = 0;

        for (BaselineCost bc : baselines) {
            if (bc.warehouseId().equals(wh.getStoreId())) {
                baselineCost = bc.avgShippingCost().doubleValue();
                sampleSize = bc.sampleSize();
                break;
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

    private double calculateDistance(CustomerAddress addr, DarkStore wh) {
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
