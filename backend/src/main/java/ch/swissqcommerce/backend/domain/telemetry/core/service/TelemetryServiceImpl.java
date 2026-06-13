package ch.swissqcommerce.backend.domain.telemetry.core.service;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.in.TelemetryUseCase;
import ch.swissqcommerce.backend.domain.telemetry.port.out.GeoLocationPort;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryServiceImpl implements TelemetryUseCase {

    private static final Logger log = LoggerFactory.getLogger(TelemetryServiceImpl.class);

    private final java.util.concurrent.ConcurrentHashMap<Integer, ThermalBreachTracker>
            activeBreaches = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<TelemetryTick> tickBuffer =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, LocationTimestamp> lastLocations =
            new ConcurrentHashMap<>();

    @Autowired private TelemetryPort telemetryPort;

    @Autowired private LedgerUseCase ledgerUseCase;

    @Autowired private GeoLocationPort geoLocationPort;

    public static class TelemetryTick {
        private final Integer orderId;
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final BigDecimal temperature;
        private final boolean dryIceInjected;

        public TelemetryTick(
                Integer orderId,
                BigDecimal latitude,
                BigDecimal longitude,
                BigDecimal temperature,
                boolean dryIceInjected) {
            this.orderId = orderId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.temperature = temperature;
            this.dryIceInjected = dryIceInjected;
        }

        public Integer getOrderId() {
            return orderId;
        }

        public BigDecimal getLatitude() {
            return latitude;
        }

        public BigDecimal getLongitude() {
            return longitude;
        }

        public BigDecimal getTemperature() {
            return temperature;
        }

        public boolean isDryIceInjected() {
            return dryIceInjected;
        }
    }

    private static class LocationTimestamp {
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final long timestampMs;

        public LocationTimestamp(BigDecimal latitude, BigDecimal longitude, long timestampMs) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestampMs = timestampMs;
        }

        public BigDecimal getLatitude() {
            return latitude;
        }

        public BigDecimal getLongitude() {
            return longitude;
        }

        public long getTimestampMs() {
            return timestampMs;
        }
    }

    private static class ThermalBreachTracker {
        private java.time.OffsetDateTime lastPingTime;
        private long cumulativeSeconds;

        public ThermalBreachTracker() {
            this.lastPingTime = java.time.OffsetDateTime.now();
            this.cumulativeSeconds = 0;
        }

        public void update(boolean isBreach) {
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            long secondsSinceLastPing = java.time.Duration.between(lastPingTime, now).toSeconds();
            this.lastPingTime = now;

            if (isBreach) {
                // Add time to breach counter (cap single jump at 30s to prevent huge jumps)
                cumulativeSeconds += Math.min(secondsSinceLastPing, 30);
            } else {
                // Cooldown: deduct time slowly, ensuring single pings don't reset everything
                // instantly
                cumulativeSeconds = Math.max(0, cumulativeSeconds - (secondsSinceLastPing / 2));
            }
        }

        public long getCumulativeSeconds() {
            return cumulativeSeconds;
        }
    }

    @Override
    @Transactional
    public OrderTelemetryLog recordTelemetry(
            Integer orderId,
            BigDecimal lat,
            BigDecimal lng,
            BigDecimal temp,
            boolean dryIceInjected) {

        Order order =
                telemetryPort
                        .findOrderById(orderId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Order not found: " + orderId));

        boolean alert = temp.compareTo(new BigDecimal("8.0")) > 0;

        OrderTelemetryLog log =
                OrderTelemetryLog.builder()
                        .orderId(order.getOrderId())
                        .order(order)
                        .deviceTimestamp(OffsetDateTime.now())
                        .latitude(lat)
                        .longitude(lng)
                        .temperature(temp)
                        .dryIceInjected(dryIceInjected)
                        .alertTriggered(alert)
                        .build();

        OrderTelemetryLog savedLog = telemetryPort.save(log);

        // Check thermal spoilage threshold F21 — only in-transit cargo can spoil;
        // late/buffered ticks must not corrupt delivered or cancelled orders.
        if (temp.compareTo(new BigDecimal("12.0")) >= 0
                && "shipping".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("spoiled");
            telemetryPort.saveOrder(order);

            // Deduct Rider trust score by -30
            Rider rider = order.getRider();
            if (rider != null) {
                int oldTrust = rider.getTrustScore();
                int newTrust = Math.max(0, oldTrust - 30);
                rider.setTrustScore(newTrust);
                telemetryPort.saveRider(rider);

                // Audit trust delta
                SecurityTrustLedger audit =
                        SecurityTrustLedger.builder()
                                .actorType("rider")
                                .actorId(rider.getRiderId())
                                .event("COLD-BREACH-TEMP-SPIKE")
                                .delta(-30)
                                .currentValue(newTrust)
                                .build();
                telemetryPort.saveTrustLedger(audit);
            }

            // Log cargo write-off debit system ledger
            List<LedgerUseCase.LedgerLeg> legs =
                    List.of(
                            new LedgerUseCase.LedgerLeg(
                                    "system", null, order.getTotalAmount(), BigDecimal.ZERO),
                            new LedgerUseCase.LedgerLeg(
                                    "system", null, BigDecimal.ZERO, order.getTotalAmount()));
            ledgerUseCase.recordTransaction(
                    "COLD-BREACH", " Perishable cargo spoilage write-off", legs);
        }

        return OrderTelemetryLog.builder()
                .logId(savedLog.getLogId())
                .orderId(savedLog.getOrderId())
                // Use the local order: the persistence adapter does not round-trip the
                // back-reference, so savedLog.getOrder() is always null in production.
                .order(order)
                .deviceTimestamp(savedLog.getDeviceTimestamp())
                .serverTimestamp(savedLog.getServerTimestamp())
                .latitude(savedLog.getLatitude())
                .longitude(savedLog.getLongitude())
                .temperature(savedLog.getTemperature())
                .dryIceInjected(savedLog.getDryIceInjected())
                .alertTriggered(savedLog.getAlertTriggered())
                .build();
    }

    @Override
    public boolean isThermalBreachActive(Integer orderId, BigDecimal currentTemp) {
        if (currentTemp == null) return false;

        boolean thresholdBreached = currentTemp.compareTo(new BigDecimal("8.0")) > 0;

        ThermalBreachTracker tracker =
                activeBreaches.computeIfAbsent(orderId, k -> new ThermalBreachTracker());
        tracker.update(thresholdBreached);

        if (tracker.getCumulativeSeconds() >= 180) {
            return true;
        } else if (tracker.getCumulativeSeconds() == 0 && !thresholdBreached) {
            // Memory cleanup if fully cooled down
            activeBreaches.remove(orderId);
            return false;
        }
        return false;
    }

    @Override
    @Transactional
    public void injectDryIce(Integer orderId) {
        activeBreaches.remove(orderId);

        Order order =
                telemetryPort
                        .findOrderById(orderId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Order not found: " + orderId));

        if (!"shipping".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException(
                    "Coolant can only be injected during shipping transit.");
        }
        if (order.getRider() == null) {
            throw new IllegalStateException(
                    "Order has no assigned rider; cannot record coolant telemetry.");
        }

        // Charge merchant $2.00
        List<LedgerUseCase.LedgerLeg> legs =
                List.of(
                        new LedgerUseCase.LedgerLeg(
                                "system", null, new BigDecimal("2.00"), BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg(
                                "system", null, BigDecimal.ZERO, new BigDecimal("2.00")));
        ledgerUseCase.recordTransaction(
                "DRY-ICE-DEBIT", "Dry ice cargo cooling mitigation fee", legs);

        // Record a clean telemetry tick resetting temp
        recordTelemetry(
                orderId,
                order.getRider().getActiveLat(),
                order.getRider().getActiveLng(),
                new BigDecimal("4.0"),
                true);
    }

    @Override
    public boolean updateLocation(
            Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp) {
        long now = System.currentTimeMillis();
        LocationTimestamp last = lastLocations.get(orderId);
        if (last != null) {
            double timeDeltaSeconds = (now - last.getTimestampMs()) / 1000.0;
            if (timeDeltaSeconds > 0.5) { // Protect division by zero
                double lat1 = last.getLatitude().doubleValue();
                double lng1 = last.getLongitude().doubleValue();
                double lat2 = lat.doubleValue();
                double lng2 = lng.doubleValue();

                double dLat = lat2 - lat1;
                double dLng = lng2 - lng1;
                double latRad = Math.toRadians((lat1 + lat2) / 2.0);
                double distLat = dLat * 111000.0;
                double distLng = dLng * 111000.0 * Math.cos(latRad);
                double distance = Math.sqrt(distLat * distLat + distLng * distLng);

                double speed = distance / timeDeltaSeconds; // meters per second
                if (speed > 41.67) { // 150 km/h is 41.67 m/s
                    log.warn(
                            "TelemetryServiceImpl: Discarded GPS outlier for order {}. Calculated"
                                    + " speed: {} m/s",
                            orderId,
                            speed);
                    return false; // Discard coordinate update
                }
            }
        }
        lastLocations.put(orderId, new LocationTimestamp(lat, lng, now));
        geoLocationPort.updateLocation(orderId, lat, lng, temp);
        return true;
    }

    @Override
    public GeoLocationPort.RiderLocation getLatestLocation(Integer orderId) {
        return geoLocationPort.getLatestLocation(orderId);
    }

    @Override
    public void queueTick(
            Integer orderId,
            BigDecimal lat,
            BigDecimal lng,
            BigDecimal temp,
            boolean dryIceInjected) {
        tickBuffer.add(new TelemetryTick(orderId, lat, lng, temp, dryIceInjected));
    }

    @Override
    @Transactional
    public void flushTickBuffer() {
        if (tickBuffer.isEmpty()) return;
        java.util.List<TelemetryTick> ticksToFlush = new java.util.ArrayList<>();
        TelemetryTick tick;
        while ((tick = tickBuffer.poll()) != null) {
            ticksToFlush.add(tick);
        }
        if (ticksToFlush.isEmpty()) return;
        for (TelemetryTick request : ticksToFlush) {
            try {
                recordTelemetry(
                        request.getOrderId(),
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getTemperature(),
                        request.isDryIceInjected());
            } catch (NoSuchElementException e) {
                // Order is gone — the tick can never succeed; drop it instead of
                // re-queueing a poison entry that would retry on every flush forever.
                log.warn("Dropping telemetry tick for missing order {}", request.getOrderId());
            } catch (Exception e) {
                tickBuffer.add(request); // Re-queue on transient failure
            }
        }
    }

    @Override
    public void cleanupOrder(Integer orderId) {
        lastLocations.remove(orderId);
        activeBreaches.remove(orderId);
        log.info("TelemetryServiceImpl: Cleaned up tracking for order {}", orderId);
    }
}
