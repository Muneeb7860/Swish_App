package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TelemetryService {

    final java.util.concurrent.ConcurrentHashMap<Integer, java.time.OffsetDateTime> activeBreaches = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private OrderTelemetryLogRepository telemetryLogRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private SecurityTrustLedgerRepository trustLedgerRepository;

    @Autowired
    private ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerUseCase;

    /**
     * Records a sensor tick from the courier's IoT device.
     * Triggers spoilage write-offs and trust penalties if thermal breach limits are hit.
     */
    @Transactional
    public OrderTelemetryLog recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, 
                                             BigDecimal temp, boolean dryIceInjected) {
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        boolean alert = temp.compareTo(new BigDecimal("8.0")) > 0;

        OrderTelemetryLog log = OrderTelemetryLog.builder()
                .order(order)
                .deviceTimestamp(OffsetDateTime.now())
                .latitude(lat)
                .longitude(lng)
                .temperature(temp)
                .dryIceInjected(dryIceInjected)
                .alertTriggered(alert)
                .build();

        OrderTelemetryLog savedLog = telemetryLogRepository.save(log);

        // Check thermal spoilage threshold F21
        if (temp.compareTo(new BigDecimal("12.0")) >= 0 && !"spoiled".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("spoiled");
            orderRepository.save(order);

            // Deduct Rider trust score by -30
            Rider rider = order.getRider();
            if (rider != null) {
                int oldTrust = rider.getTrustScore();
                int newTrust = Math.max(0, oldTrust - 30);
                rider.setTrustScore(newTrust);
                riderRepository.save(rider);

                // Audit trust delta
                SecurityTrustLedger audit = SecurityTrustLedger.builder()
                        .actorType("rider")
                        .actorId(rider.getRiderId())
                        .event("COLD-BREACH-TEMP-SPIKE")
                        .delta(-30)
                        .currentValue(newTrust)
                        .build();
                trustLedgerRepository.save(audit);
            }

            // Log cargo write-off debit system ledger
            List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> legs = List.of(
                new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, order.getTotalAmount(), BigDecimal.ZERO),
                new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, order.getTotalAmount()) // double entry internal writeoff
            );
            ledgerUseCase.recordTransaction("COLD-BREACH", " Ruined cargo perishable spoilage write-off", legs);
        }

        return savedLog;
    }

    /**
     * Checks if a thermal breach warning has been continuously active for more than 3 minutes (180 seconds).
     */
    public boolean isThermalBreachActive(Integer orderId, BigDecimal currentTemp) {
        if (currentTemp == null) return false;
        
        // Match 8.0°C alert threshold in recordTelemetry
        boolean thresholdBreached = currentTemp.compareTo(new BigDecimal("8.0")) > 0;
        
        if (thresholdBreached) {
            java.time.OffsetDateTime start = activeBreaches.computeIfAbsent(orderId, k -> java.time.OffsetDateTime.now());
            long secondsPassed = java.time.Duration.between(start, java.time.OffsetDateTime.now()).toSeconds();
            return secondsPassed >= 180;
        } else {
            activeBreaches.remove(orderId);
            return false;
        }
    }

    /**
     * Injects dry ice coolant to reset temperature back to 4.0°C.
     * Charges merchant $2.00 and logs ledger transaction.
     */
    @Transactional
    public void injectDryIce(Integer orderId) {
        activeBreaches.remove(orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (!"shipping".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Coolant can only be injected during shipping transit.");
        }

        // Charge merchant $2.00
        List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> legs = List.of(
            new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, new BigDecimal("2.00"), BigDecimal.ZERO),
            new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, new BigDecimal("2.00")) // balanced transit charge
        );
        ledgerUseCase.recordTransaction("DRY-ICE-DEBIT", "Dry ice cargo cooling mitigation fee", legs);

        // Record a clean telemetry tick resetting temp
        recordTelemetry(orderId, order.getRider().getActiveLat(), order.getRider().getActiveLng(), 
                        new BigDecimal("4.0"), true);
    }
}
