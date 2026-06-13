package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.repository.OrderRepository;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelemetryPersistenceAdapter implements TelemetryPort {

    private final OrderTelemetryLogRepository telemetryLogRepository;
    private final OrderRepository orderRepository;
    private final RiderRepository riderRepository;
    private final SecurityTrustLedgerRepository securityTrustLedgerRepository;

    private OrderTelemetryLogEntity toEntity(OrderTelemetryLog log) {
        if (log == null) return null;
        return OrderTelemetryLogEntity.builder()
                .logId(log.getLogId())
                .orderId(log.getOrderId())
                .deviceTimestamp(log.getDeviceTimestamp())
                .serverTimestamp(log.getServerTimestamp())
                .latitude(log.getLatitude())
                .longitude(log.getLongitude())
                .temperature(log.getTemperature())
                .dryIceInjected(log.getDryIceInjected())
                .alertTriggered(log.getAlertTriggered())
                .build();
    }

    private OrderTelemetryLog toDomain(OrderTelemetryLogEntity entity) {
        if (entity == null) return null;
        return OrderTelemetryLog.builder()
                .logId(entity.getLogId())
                .orderId(entity.getOrderId())
                .deviceTimestamp(entity.getDeviceTimestamp())
                .serverTimestamp(entity.getServerTimestamp())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .temperature(entity.getTemperature())
                .dryIceInjected(entity.getDryIceInjected())
                .alertTriggered(entity.getAlertTriggered())
                .build();
    }

    private Order mapToDomain(OrderEntity entity) {
        if (entity == null) return null;
        Order order =
                Order.builder()
                        .orderId(entity.getOrderId())
                        .customer(entity.getCustomer())
                        .store(entity.getStore())
                        .rider(mapToDomainRider(entity.getRider()))
                        .totalAmount(entity.getTotalAmount())
                        .weatherSurcharge(entity.getWeatherSurcharge())
                        .tipAmount(entity.getTipAmount())
                        .paymentMethod(entity.getPaymentMethod())
                        .status(entity.getStatus())
                        .slaCountdownSec(entity.getSlaCountdownSec())
                        .bagsReturned(entity.getBagsReturned())
                        .idempotencyKey(entity.getIdempotencyKey())
                        .promisedBy(entity.getPromisedBy())
                        .containsPerishables(entity.getContainsPerishables())
                        .minCartValueMet(entity.getMinCartValueMet())
                        .storeFaultWaiverApplied(entity.getStoreFaultWaiverApplied())
                        .perishableMaintenanceFee(entity.getPerishableMaintenanceFee())
                        .priceLockedAt(entity.getPriceLockedAt())
                        .createdAt(entity.getCreatedAt())
                        .build();

        if (entity.getOrderItems() != null) {
            order.setOrderItems(
                    new java.util.ArrayList<>(
                            entity.getOrderItems().stream()
                                    .map(
                                            itemEntity ->
                                                    ch.swissqcommerce.backend.domain.transaction
                                                            .core.model.OrderItem.builder()
                                                            .order(order)
                                                            .item(itemEntity.getItem())
                                                            .quantity(itemEntity.getQuantity())
                                                            .price(itemEntity.getPrice())
                                                            .build())
                                    .toList()));
        }
        return order;
    }

    private OrderEntity mapToEntity(Order domain) {
        if (domain == null) return null;
        OrderEntity entity =
                OrderEntity.builder()
                        .orderId(domain.getOrderId())
                        .customer(domain.getCustomer())
                        .store(domain.getStore())
                        .rider(mapToEntityRider(domain.getRider()))
                        .totalAmount(domain.getTotalAmount())
                        .weatherSurcharge(
                                domain.getWeatherSurcharge() != null
                                        ? domain.getWeatherSurcharge()
                                        : java.math.BigDecimal.ZERO)
                        .tipAmount(
                                domain.getTipAmount() != null
                                        ? domain.getTipAmount()
                                        : java.math.BigDecimal.ZERO)
                        .paymentMethod(domain.getPaymentMethod())
                        .status(domain.getStatus() != null ? domain.getStatus() : "pending")
                        .slaCountdownSec(
                                domain.getSlaCountdownSec() != null
                                        ? domain.getSlaCountdownSec()
                                        : 540)
                        .bagsReturned(
                                domain.getBagsReturned() != null ? domain.getBagsReturned() : 0)
                        .idempotencyKey(domain.getIdempotencyKey())
                        .promisedBy(domain.getPromisedBy())
                        .containsPerishables(
                                domain.getContainsPerishables() != null
                                        ? domain.getContainsPerishables()
                                        : false)
                        .minCartValueMet(
                                domain.getMinCartValueMet() != null
                                        ? domain.getMinCartValueMet()
                                        : true)
                        .storeFaultWaiverApplied(
                                domain.getStoreFaultWaiverApplied() != null
                                        ? domain.getStoreFaultWaiverApplied()
                                        : false)
                        .perishableMaintenanceFee(
                                domain.getPerishableMaintenanceFee() != null
                                        ? domain.getPerishableMaintenanceFee()
                                        : java.math.BigDecimal.ZERO)
                        .priceLockedAt(domain.getPriceLockedAt())
                        .createdAt(domain.getCreatedAt())
                        .build();

        if (domain.getOrderItems() != null) {
            entity.setOrderItems(
                    new java.util.ArrayList<>(
                            domain.getOrderItems().stream()
                                    .map(
                                            domainItem -> {
                                                OrderItemEntity itemEntity = new OrderItemEntity();
                                                itemEntity.setOrder(entity);
                                                itemEntity.setItem(domainItem.getItem());
                                                itemEntity.setQuantity(domainItem.getQuantity());
                                                itemEntity.setPrice(domainItem.getPrice());
                                                return itemEntity;
                                            })
                                    .toList()));
        }
        return entity;
    }

    private Rider mapToDomainRider(RiderEntity entity) {
        if (entity == null) return null;
        return Rider.builder()
                .riderId(entity.getRiderId())
                .fullName(entity.getFullName())
                .vehicleType(entity.getVehicleType())
                .onboardingStatus(entity.getOnboardingStatus())
                .walletBalance(entity.getWalletBalance())
                .activeLat(entity.getActiveLat())
                .activeLng(entity.getActiveLng())
                .trustScore(entity.getTrustScore())
                .cashCollectedLimit(entity.getCashCollectedLimit())
                .currentCashInHand(entity.getCurrentCashInHand())
                .activeShiftId(entity.getActiveShiftId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RiderEntity mapToEntityRider(Rider domain) {
        if (domain == null) return null;
        RiderEntity entity = new RiderEntity();
        entity.setRiderId(domain.getRiderId());
        entity.setFullName(domain.getFullName());
        entity.setVehicleType(domain.getVehicleType());
        entity.setOnboardingStatus(domain.getOnboardingStatus());
        entity.setWalletBalance(domain.getWalletBalance());
        entity.setActiveLat(domain.getActiveLat());
        entity.setActiveLng(domain.getActiveLng());
        entity.setTrustScore(domain.getTrustScore() != null ? domain.getTrustScore() : 100);
        entity.setCashCollectedLimit(domain.getCashCollectedLimit());
        entity.setCurrentCashInHand(
                domain.getCurrentCashInHand() != null
                        ? domain.getCurrentCashInHand()
                        : java.math.BigDecimal.ZERO);
        entity.setActiveShiftId(domain.getActiveShiftId());
        return entity;
    }

    @Override
    public OrderTelemetryLog save(OrderTelemetryLog log) {
        if (log == null) return null;
        OrderTelemetryLogEntity entity = toEntity(log);
        OrderTelemetryLogEntity saved = telemetryLogRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<OrderTelemetryLog> findByOrderId(Integer orderId) {
        return telemetryLogRepository.findByOrderIdOrderByDeviceTimestampDesc(orderId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Order> findOrderById(Integer orderId) {
        return orderRepository.findById(orderId).map(this::mapToDomain);
    }

    @Override
    public Order saveOrder(Order order) {
        if (order == null) return null;
        OrderEntity entity = mapToEntity(order);
        OrderEntity saved = orderRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<Rider> findRiderById(String riderId) {
        return riderRepository.findById(riderId).map(this::mapToDomainRider);
    }

    @Override
    public Rider saveRider(Rider rider) {
        if (rider == null) return null;
        RiderEntity entity = mapToEntityRider(rider);
        RiderEntity saved = riderRepository.save(entity);
        return mapToDomainRider(saved);
    }

    @Override
    public SecurityTrustLedger saveTrustLedger(SecurityTrustLedger ledger) {
        if (ledger == null) return null;
        return securityTrustLedgerRepository.save(ledger);
    }
}
