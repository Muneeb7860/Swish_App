package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransactionPersistenceAdapter
        implements CustomerPort,
                RiderPort,
                InventoryPort,
                DarkStorePort,
                SystemConfigPort,
                OutboxEventPort,
                OrderPort,
                HitlQueuePort {

    private final CustomerRepository customerRepository;
    private final RiderRepository riderRepository;
    private final InventoryRepository inventoryRepository;
    private final DarkStoreRepository darkStoreRepository;
    private final SystemConfigurationRepository systemConfigurationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final HitlQueueRepository hitlQueueRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    public TransactionPersistenceAdapter(
            CustomerRepository customerRepository,
            RiderRepository riderRepository,
            InventoryRepository inventoryRepository,
            DarkStoreRepository darkStoreRepository,
            SystemConfigurationRepository systemConfigurationRepository,
            OutboxEventRepository outboxEventRepository,
            OrderRepository orderRepository,
            HitlQueueRepository hitlQueueRepository,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
        this.customerRepository = customerRepository;
        this.riderRepository = riderRepository;
        this.inventoryRepository = inventoryRepository;
        this.darkStoreRepository = darkStoreRepository;
        this.systemConfigurationRepository = systemConfigurationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.hitlQueueRepository = hitlQueueRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public Optional<Customer> findCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    public List<Rider> findAll() {
        return riderRepository.findAll().stream()
                .map(
                        entity ->
                                Rider.builder()
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
                                        .build())
                .toList();
    }

    @Override
    public Optional<Inventory> findInventoryById(String id) {
        return inventoryRepository.findById(id);
    }

    @Override
    public Inventory save(Inventory inventory) {
        return inventoryRepository.save(inventory);
    }

    @Override
    public Optional<DarkStore> findDarkStoreById(String id) {
        return darkStoreRepository.findById(id);
    }

    @Override
    public String getSystemConfig(String key, String defaultValue) {
        return systemConfigurationRepository
                .findById(key)
                .map(SystemConfiguration::getConfigValue)
                .orElse(defaultValue);
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEvent savedEvent = outboxEventRepository.save(event);
        applicationEventPublisher.publishEvent(
                new ch.swissqcommerce.backend.domain.event.core.model.OutboxEventSavedEvent(
                        savedEvent));
        return savedEvent;
    }

    @Override
    public Optional<Order> findById(Integer id) {
        return orderRepository.findById(id).map(this::mapToDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey).map(this::mapToDomain);
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = mapToEntity(order);
        OrderEntity savedEntity = orderRepository.save(entity);
        return mapToDomain(savedEntity);
    }

    @Override
    public void flush() {
        orderRepository.flush();
    }

    @Override
    public List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId) {
        return orderRepository.findByCustomerCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::mapToDomain)
                .toList();
    }

    @Override
    public List<Order> findAllById(List<Integer> ids) {
        return orderRepository.findAllById(ids).stream().map(this::mapToDomain).toList();
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
                        .deliveryPin(entity.getDeliveryPin())
                        .proofOfDeliveryPhotoUrl(entity.getProofOfDeliveryPhotoUrl())
                        .rejectionReason(entity.getRejectionReason())
                        .rejectionPhotoUrl(entity.getRejectionPhotoUrl())
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
                        .deliveryPin(domain.getDeliveryPin())
                        .proofOfDeliveryPhotoUrl(domain.getProofOfDeliveryPhotoUrl())
                        .rejectionReason(domain.getRejectionReason())
                        .rejectionPhotoUrl(domain.getRejectionPhotoUrl())
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
                .gearExempt(entity.isGearExempt())
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
        entity.setGearExempt(domain.isGearExempt());
        return entity;
    }

    @Override
    public HitlQueue save(HitlQueue queue) {
        return hitlQueueRepository.save(queue);
    }
}
