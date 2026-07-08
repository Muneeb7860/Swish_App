package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.core.model.OrderItem;
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
        Order order = new Order();
        order.setOrderId(entity.getOrderId());
        order.setCustomer(entity.getCustomer());
        order.setStore(entity.getStore());
        order.setRider(mapToDomainRider(entity.getRider()));
        order.setTotalAmount(entity.getTotalAmount());
        order.setWeatherSurcharge(entity.getWeatherSurcharge());
        order.setTipAmount(entity.getTipAmount());
        order.setPaymentMethod(entity.getPaymentMethod());
        order.setStatus(entity.getStatus());
        order.setSlaCountdownSec(entity.getSlaCountdownSec());
        order.setBagsReturned(entity.getBagsReturned());
        order.setIdempotencyKey(entity.getIdempotencyKey());
        order.setPromisedBy(entity.getPromisedBy());
        order.setContainsPerishables(entity.getContainsPerishables());
        order.setMinCartValueMet(entity.getMinCartValueMet());
        order.setStoreFaultWaiverApplied(entity.getStoreFaultWaiverApplied());
        order.setPerishableMaintenanceFee(entity.getPerishableMaintenanceFee());
        order.setPriceLockedAt(entity.getPriceLockedAt());
        order.setCreatedAt(entity.getCreatedAt());

        if (entity.getOrderItems() != null) {
            order.setOrderItems(
                    new java.util.ArrayList<OrderItem>(
                            entity.getOrderItems().stream()
                                    .map(
                                            itemEntity -> {
                                                OrderItem item = new OrderItem();
                                                item.setOrder(order);
                                                item.setItem(itemEntity.getItem());
                                                item.setQuantity(itemEntity.getQuantity());
                                                item.setPrice(itemEntity.getPrice());
                                                return item;
                                            })
                                    .toList()));
        }
        return order;
    }

    private OrderEntity mapToEntity(Order domain) {
        if (domain == null) return null;
        OrderEntity entity = new OrderEntity();
        entity.setOrderId(domain.getOrderId());
        entity.setCustomer(domain.getCustomer());
        entity.setStore(domain.getStore());
        entity.setRider(mapToEntityRider(domain.getRider()));
        entity.setTotalAmount(domain.getTotalAmount());
        entity.setWeatherSurcharge(
                domain.getWeatherSurcharge() != null
                        ? domain.getWeatherSurcharge()
                        : java.math.BigDecimal.ZERO);
        entity.setTipAmount(
                domain.getTipAmount() != null ? domain.getTipAmount() : java.math.BigDecimal.ZERO);
        entity.setPaymentMethod(domain.getPaymentMethod());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus() : "pending");
        entity.setSlaCountdownSec(
                domain.getSlaCountdownSec() != null ? domain.getSlaCountdownSec() : 540);
        entity.setBagsReturned(domain.getBagsReturned() != null ? domain.getBagsReturned() : 0);
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setPromisedBy(domain.getPromisedBy());
        entity.setContainsPerishables(
                domain.getContainsPerishables() != null ? domain.getContainsPerishables() : false);
        entity.setMinCartValueMet(
                domain.getMinCartValueMet() != null ? domain.getMinCartValueMet() : true);
        entity.setStoreFaultWaiverApplied(
                domain.getStoreFaultWaiverApplied() != null
                        ? domain.getStoreFaultWaiverApplied()
                        : false);
        entity.setPerishableMaintenanceFee(
                domain.getPerishableMaintenanceFee() != null
                        ? domain.getPerishableMaintenanceFee()
                        : java.math.BigDecimal.ZERO);
        entity.setPriceLockedAt(domain.getPriceLockedAt());
        entity.setCreatedAt(domain.getCreatedAt());

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
