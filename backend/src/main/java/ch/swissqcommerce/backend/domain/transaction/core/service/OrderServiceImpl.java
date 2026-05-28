package ch.swissqcommerce.backend.domain.transaction.core.service;

import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public class OrderServiceImpl implements OrderUseCase {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final DarkStoreRepository darkStoreRepository;
    private final RiderRepository riderRepository;
    private final InventoryRepository inventoryRepository;
    private final SystemConfigurationRepository systemConfigurationRepository;
    private final ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerUseCase;
    private final ch.swissqcommerce.backend.repository.OutboxEventRepository outboxRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public OrderServiceImpl(OrderRepository orderRepository,
                            CustomerRepository customerRepository,
                            DarkStoreRepository darkStoreRepository,
                            RiderRepository riderRepository,
                            InventoryRepository inventoryRepository,
                            SystemConfigurationRepository systemConfigurationRepository,
                            ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerUseCase,
                            ch.swissqcommerce.backend.repository.OutboxEventRepository outboxRepository,
                            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.darkStoreRepository = darkStoreRepository;
        this.riderRepository = riderRepository;
        this.inventoryRepository = inventoryRepository;
        this.systemConfigurationRepository = systemConfigurationRepository;
        this.ledgerUseCase = ledgerUseCase;
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
    }

    public Order checkout(String customerId, List<CartItem> items, String paymentMethod, 
                           BigDecimal tip, Integer bagsReturned, String idempotencyKey) {
        
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cart items cannot be null or empty");
        }

        if (idempotencyKey != null) {
            Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOrder.isPresent()) {
                return existingOrder.get();
            }
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found: " + customerId));

        String storeId = evaluateCheckoutRouting(items);
        DarkStore store = darkStoreRepository.findById(storeId)
                .orElseThrow(() -> new NoSuchElementException("Dark Store not found: " + storeId));

        BigDecimal cartSubtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        Order order = Order.builder()
                .customer(customer)
                .store(store)
                .paymentMethod(paymentMethod)
                .bagsReturned(bagsReturned)
                .idempotencyKey(idempotencyKey)
                .tipAmount(tip)
                .build();

        for (CartItem cartItem : items) {
            Inventory inventory = inventoryRepository.findById(cartItem.itemId())
                    .orElseThrow(() -> new NoSuchElementException("Item not found: " + cartItem.itemId()));

            if (inventory.getStock() < cartItem.quantity()) {
                throw new IllegalStateException("Insufficient stock for item: " + inventory.getName());
            }

            inventory.setStock(inventory.getStock() - cartItem.quantity());
            inventoryRepository.save(inventory);

            BigDecimal itemCost = inventory.getPrice().multiply(BigDecimal.valueOf(cartItem.quantity()));
            cartSubtotal = cartSubtotal.add(itemCost);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .item(inventory)
                    .quantity(cartItem.quantity())
                    .price(inventory.getPrice())
                    .build();
            orderItems.add(orderItem);
        }
        order.setOrderItems(orderItems);

        BigDecimal weatherSurcharge = BigDecimal.ZERO;
        String weather = getSystemConfig("current_weather", "Sunny");
        if (!"Sunny".equalsIgnoreCase(weather)) {
            weatherSurcharge = new BigDecimal("3.00");
        }
        order.setWeatherSurcharge(weatherSurcharge);

        int baseSlaSeconds = 540;
        if ("East Store".equalsIgnoreCase(store.getStoreName())) {
            baseSlaSeconds += 240;
        }
        order.setSlaCountdownSec(baseSlaSeconds);

        BigDecimal totalCheckoutCost = cartSubtotal.add(weatherSurcharge);
        BigDecimal customerTotalDebit = totalCheckoutCost.add(tip);
        
        BigDecimal esgRebate = BigDecimal.ZERO;
        if (bagsReturned > 0) {
            esgRebate = new BigDecimal("0.50").multiply(BigDecimal.valueOf(bagsReturned));
            customerTotalDebit = customerTotalDebit.subtract(esgRebate);
        }
        order.setTotalAmount(totalCheckoutCost);

        Rider rider = findAvailableRider();
        order.setRider(rider);
        order.setStatus("pending");

        Order savedOrder = orderRepository.save(order);

        List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> legs = new ArrayList<>();
        legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("customer", customerId, customerTotalDebit, BigDecimal.ZERO));
        legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, totalCheckoutCost));
        
        if (tip.compareTo(BigDecimal.ZERO) > 0 && rider != null) {
            legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("rider", rider.getRiderId(), BigDecimal.ZERO, tip));
        }

        if (esgRebate.compareTo(BigDecimal.ZERO) > 0) {
            legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, esgRebate, BigDecimal.ZERO));
        }

        ledgerUseCase.recordTransaction("ORDER-PAY", "Order checkout payments and tip release", legs);

        int itemsCount = items.stream().mapToInt(CartItem::quantity).sum();
        int points = itemsCount * 10;
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        customerRepository.save(customer);

        eventPublisher.publishEvent(new ch.swissqcommerce.backend.domain.event.core.model.OrderFulfilledEvent(customerId, savedOrder.getOrderId().toString(), points));

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(savedOrder.getOrderId().toString())
                .eventType("order.placed")
                .payload("{\"orderId\": " + savedOrder.getOrderId() + ", \"totalAmount\": " + savedOrder.getTotalAmount() + "}")
                .build();
        outboxRepository.save(event);

        return savedOrder;
    }

    public Order checkoutFallback(String customerId, List<CartItem> items, String paymentMethod, 
                           BigDecimal tip, Integer bagsReturned, String idempotencyKey, Throwable t) {
        throw new IllegalStateException("Checkout service is temporarily unavailable due to high load or database latency. Please try again later. Root cause: " + t.getMessage(), t);
    }

    private String evaluateCheckoutRouting(List<CartItem> items) {
        String selectedStore = "Central Store";

        for (CartItem item : items) {
            Inventory inv = inventoryRepository.findById(item.itemId()).orElse(null);
            if (inv != null && "Central Store".equalsIgnoreCase(inv.getStore().getStoreName())) {
                if (inv.getStock() < item.quantity()) {
                    return "East Store";
                }
            }
        }

        int backlog = Integer.parseInt(getSystemConfig("central_picker_backlog", "0"));
        if (backlog > 1) {
            return "East Store";
        }

        return "Central Store";
    }

    private Rider findAvailableRider() {
        return riderRepository.findAll().stream()
                .filter(r -> "active".equalsIgnoreCase(r.getOnboardingStatus()))
                .findFirst()
                .orElse(null);
    }

    private String getSystemConfig(String key, String defaultValue) {
        return systemConfigurationRepository.findById(key)
                .map(SystemConfiguration::getConfigValue)
                .orElse(defaultValue);
    }
}
