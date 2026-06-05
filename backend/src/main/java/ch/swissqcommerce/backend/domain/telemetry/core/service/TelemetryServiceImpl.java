package ch.swissqcommerce.backend.domain.telemetry.core.service;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.in.TelemetryUseCase;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.repository.OrderRepository;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TelemetryServiceImpl implements TelemetryUseCase {

    private final java.util.concurrent.ConcurrentHashMap<Integer, java.time.OffsetDateTime> activeBreaches = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private TelemetryPort telemetryPort;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private SecurityTrustLedgerRepository trustLedgerRepository;

    @Autowired
    private LedgerUseCase ledgerUseCase;

    @Override
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

        OrderTelemetryLog savedLog = telemetryPort.save(log);

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
            List<LedgerUseCase.LedgerLeg> legs = List.of(
                new LedgerUseCase.LedgerLeg("system", null, order.getTotalAmount(), BigDecimal.ZERO),
                new LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, order.getTotalAmount())
            );
            ledgerUseCase.recordTransaction("COLD-BREACH", " Ruined cargo perishable spoilage write-off", legs);
        }

        return savedLog;
    }

    @Override
    public boolean isThermalBreachActive(Integer orderId, BigDecimal currentTemp) {
        if (currentTemp == null) return false;
        
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

    @Override
    @Transactional
    public void injectDryIce(Integer orderId) {
        activeBreaches.remove(orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (!"shipping".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Coolant can only be injected during shipping transit.");
        }

        // Charge merchant $2.00
        List<LedgerUseCase.LedgerLeg> legs = List.of(
            new LedgerUseCase.LedgerLeg("system", null, new BigDecimal("2.00"), BigDecimal.ZERO),
            new LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, new BigDecimal("2.00"))
        );
        ledgerUseCase.recordTransaction("DRY-ICE-DEBIT", "Dry ice cargo cooling mitigation fee", legs);

        // Record a clean telemetry tick resetting temp
        recordTelemetry(orderId, order.getRider().getActiveLat(), order.getRider().getActiveLng(), 
                        new BigDecimal("4.0"), true);
    }
}
