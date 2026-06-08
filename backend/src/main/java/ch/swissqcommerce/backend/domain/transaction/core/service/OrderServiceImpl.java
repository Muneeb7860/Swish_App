package ch.swissqcommerce.backend.domain.transaction.core.service;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.Inventory;


import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.*;
import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import ch.swissqcommerce.backend.domain.transaction.port.out.HitlQueuePort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Hardened core Order Processing Service.
 * Leverages transactional boundaries, optimistic lock retries,
 * hexagonal ports for decoupling, and unique key database idempotency protection.
 */
public class OrderServiceImpl implements OrderUseCase {

    private final OrderPort orderPort;
    private final CustomerPort customerPort;
    private final DarkStorePort darkStorePort;
    private final RiderPort riderPort;
    private final InventoryPort inventoryPort;
    private final SystemConfigPort systemConfigPort;
    private final ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerUseCase;
    private final OutboxEventPort outboxEventPort;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final HitlQueuePort hitlQueuePort;

    public OrderServiceImpl(OrderPort orderPort,
                            CustomerPort customerPort,
                            DarkStorePort darkStorePort,
                            RiderPort riderPort,
                            InventoryPort inventoryPort,
                            SystemConfigPort systemConfigPort,
                            ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerUseCase,
                            OutboxEventPort outboxEventPort,
                            org.springframework.context.ApplicationEventPublisher eventPublisher,
                            HitlQueuePort hitlQueuePort) {
        this.orderPort = orderPort;
        this.customerPort = customerPort;
        this.darkStorePort = darkStorePort;
        this.riderPort = riderPort;
        this.inventoryPort = inventoryPort;
        this.systemConfigPort = systemConfigPort;
        this.ledgerUseCase = ledgerUseCase;
        this.outboxEventPort = outboxEventPort;
        this.eventPublisher = eventPublisher;
        this.hitlQueuePort = hitlQueuePort;
    }

    @Override
    @Transactional
    @ch.swissqcommerce.backend.config.TransactionalRetry(maxRetries = 3, backoffMs = 150)
    public Order checkout(String customerId, List<CartItem> items, String paymentMethod, 
                           BigDecimal tip, Integer bagsReturned, String idempotencyKey) {
        
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cart items cannot be null or empty");
        }

        // Validate individual cart items: reject zero/negative quantities and duplicate SKUs
        Set<String> seenItemIds = new java.util.HashSet<>();
        for (CartItem cartItem : items) {
            if (cartItem.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Cart item quantity must be greater than zero, got: " + cartItem.quantity()
                                + " for item: " + cartItem.itemId());
            }
            if (!seenItemIds.add(cartItem.itemId())) {
                throw new IllegalArgumentException(
                        "Duplicate item in cart: " + cartItem.itemId()
                                + ". Consolidate quantities into a single line item.");
            }
        }

        // 1. Initial quick idempotency check
        if (idempotencyKey != null) {
            Optional<Order> existingOrder = orderPort.findByIdempotencyKey(idempotencyKey);
            if (existingOrder.isPresent()) {
                return existingOrder.get();
            }
        }

        Customer customer = customerPort.findCustomerById(customerId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found: " + customerId));

        String storeId = evaluateCheckoutRouting(items);
        DarkStore store = darkStorePort.findDarkStoreById(storeId)
                .orElseThrow(() -> new NoSuchElementException("Dark Store not found: " + storeId));

        BigDecimal cartSubtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        String generatedPin = String.format("%04d", new java.util.Random().nextInt(10000));
        
        Order order = Order.builder()
                .customer(customer)
                .store(store)
                .paymentMethod(paymentMethod)
                .bagsReturned(bagsReturned)
                .idempotencyKey(idempotencyKey)
                .tipAmount(tip)
                .deliveryPin(generatedPin)
                .build();

        boolean containsPerishable = false;
        for (CartItem cartItem : items) {
            Inventory inventory = inventoryPort.findInventoryById(cartItem.itemId())
                    .orElseThrow(() -> new NoSuchElementException("Item not found: " + cartItem.itemId()));

            if (inventory.getStock() < cartItem.quantity()) {
                throw new IllegalStateException("Insufficient stock for item: " + inventory.getName());
            }

            inventory.setStock(inventory.getStock() - cartItem.quantity());
            inventoryPort.save(inventory);

            if (Boolean.TRUE.equals(inventory.getPerishable())) {
                containsPerishable = true;
            }

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

        BigDecimal totalCheckoutCost = cartSubtotal.add(weatherSurcharge);
        BigDecimal customerTotalDebit = totalCheckoutCost.add(tip);
        
        BigDecimal esgRebate = BigDecimal.ZERO;
        if (bagsReturned > 0) {
            esgRebate = new BigDecimal("0.50").multiply(BigDecimal.valueOf(bagsReturned));
            customerTotalDebit = customerTotalDebit.subtract(esgRebate);
        }
        order.setTotalAmount(totalCheckoutCost);

        Rider rider = findOptimalRider(containsPerishable);
        order.setRider(rider);
        order.setStatus("pending");

        int baseSlaSeconds = 540;
        if (rider != null && rider.getVehicleType() != null) {
            String vType = rider.getVehicleType().toLowerCase();
            if (vType.contains("scooter")) {
                baseSlaSeconds = 420;
            } else if (vType.contains("van")) {
                baseSlaSeconds = 720;
            }
        }
        if (containsPerishable) {
            baseSlaSeconds -= 60;
        }
        if ("East Store".equalsIgnoreCase(store.getStoreName())) {
            baseSlaSeconds += 240;
        }
        order.setSlaCountdownSec(baseSlaSeconds);

        // 2. Safe unique-key checkout insert with fallback lookup
        Order savedOrder;
        try {
            savedOrder = orderPort.save(order);
            orderPort.flush(); // Flush immediately to trigger unique constraint check
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null) {
                return orderPort.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException("Idempotency key violation occurred, but safe lookup failed.", e));
            }
            throw e;
        }

        List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> legs = new ArrayList<>();
        legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("customer", customerId, customerTotalDebit, BigDecimal.ZERO));
        legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, totalCheckoutCost));
        
        if (tip.compareTo(BigDecimal.ZERO) > 0 && rider != null) {
            legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("rider", rider.getRiderId(), BigDecimal.ZERO, tip));
        }

        if (esgRebate.compareTo(BigDecimal.ZERO) > 0) {
            legs.add(new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, esgRebate, BigDecimal.ZERO));
        }

        // Ledger writes (will fail and roll back transaction if customer has insufficient funds)
        ledgerUseCase.recordTransaction("ORDER-PAY", "Order checkout payments and tip release", legs);

        int itemsCount = items.stream().mapToInt(CartItem::quantity).sum();
        int points = itemsCount * 10;
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        customerPort.save(customer);

        // Publish Spring domain event (now handled safely downstream)
        eventPublisher.publishEvent(new ch.swissqcommerce.backend.domain.event.core.model.OrderFulfilledEvent(customerId, savedOrder.getOrderId().toString(), points));

        // Persistent transactional outbox save (Eventual Consistency outbox pattern)
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(savedOrder.getOrderId().toString())
                .eventType("order.placed")
                .payload("{\"orderId\": " + savedOrder.getOrderId() + ", \"totalAmount\": " + savedOrder.getTotalAmount() + "}")
                .build();
        outboxEventPort.save(event);

        return savedOrder;
    }

    public Order checkoutFallback(String customerId, List<CartItem> items, String paymentMethod, 
                           BigDecimal tip, Integer bagsReturned, String idempotencyKey, Throwable t) {
        throw new IllegalStateException("Checkout service is temporarily unavailable due to high load or database latency. Please try again later. Root cause: " + t.getMessage(), t);
    }

    private String evaluateCheckoutRouting(List<CartItem> items) {
        for (CartItem item : items) {
            Inventory inv = inventoryPort.findInventoryById(item.itemId()).orElse(null);
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

    private Rider findOptimalRider(boolean containsPerishable) {
        List<Rider> activeRiders = riderPort.findAll().stream()
                .filter(r -> "active".equalsIgnoreCase(r.getOnboardingStatus()))
                .toList();

        if (activeRiders.isEmpty()) {
            return null;
        }

        if (containsPerishable) {
            // Prioritize Scooter (1) -> E-Bike (2) -> Van / other (3)
            return activeRiders.stream()
                    .min(Comparator.comparingInt(r -> {
                        if (r.getVehicleType() == null) return 3;
                        String type = r.getVehicleType().toLowerCase();
                        if (type.contains("scooter")) return 1;
                        if (type.contains("bike")) return 2;
                        return 3;
                    }))
                    .orElse(activeRiders.get(0));
        } else {
            // Prioritize Van (1) -> Scooter (2) -> E-Bike (3)
            return activeRiders.stream()
                    .min(Comparator.comparingInt(r -> {
                        if (r.getVehicleType() == null) return 3;
                        String type = r.getVehicleType().toLowerCase();
                        if (type.contains("van")) return 1;
                        if (type.contains("scooter")) return 2;
                        return 3;
                    }))
                    .orElse(activeRiders.get(0));
        }
    }

    private String getSystemConfig(String key, String defaultValue) {
        return systemConfigPort.getSystemConfig(key, defaultValue);
    }

    @Override
    @Transactional
    public Map<String, Object> requestRefund(Integer orderId, String claimReason, BigDecimal customerLatitude, BigDecimal customerLongitude) {
        Order order = orderPort.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found."));
        
        Customer customer = order.getCustomer();
        
        if (customer.getTrustScore() < 65) {
            return Map.of(
                "status", "rejected",
                "message", "REFUND REFUSED: Your account trust rating has fallen below safety thresholds."
            );
        }
        
        if (customerLatitude != null && order.getRider() != null) {
            BigDecimal distLat = customerLatitude.subtract(order.getRider().getActiveLat()).abs();
            if (distLat.compareTo(new BigDecimal("0.05")) > 0) {
                customer.setTrustScore(Math.max(0, customer.getTrustScore() - 25));
                customerPort.save(customer);
                return Map.of(
                    "status", "rejected",
                    "message", "REFUND BLOCKED: Telemetry Correlation GPS audit failed."
                );
            }
        }

        boolean isLateClaim = claimReason != null && (
            claimReason.toLowerCase().contains("late") || 
            claimReason.toLowerCase().contains("delay") || 
            claimReason.toLowerCase().contains("sla") || 
            claimReason.toLowerCase().contains("slow")
        );

        if (isLateClaim && order.getSlaCountdownSec() <= 0) {
            // AI-Autopilot Auto-Approval Path
            String ticketId = "AUTO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            HitlQueue ticket = HitlQueue.builder()
                    .ticketId(ticketId)
                    .type("refund_customer")
                    .customer(customer)
                    .orderId(order.getOrderId())
                    .description("AI-AUTOPILOT: Refund request auto-approved due to verified SLA breach for order " + orderId + ". Override By: ai-autopilot. Reason: Verified SLA countdown breach (countdown <= 0)")
                    .amount(order.getTotalAmount())
                    .status("approved")
                    .build();
            hitlQueuePort.save(ticket);

            // Refund Customer Wallet
            customer.setWalletBalance(customer.getWalletBalance().add(order.getTotalAmount()));
            customerPort.save(customer);

            // Double-entry Ledger Entry
            List<ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg> legs = List.of(
                new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("system", null, order.getTotalAmount(), BigDecimal.ZERO),
                new ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase.LedgerLeg("customer", customer.getCustomerId(), BigDecimal.ZERO, order.getTotalAmount())
            );
            ledgerUseCase.recordTransaction("REFUND-AUTO", "AI-Autopilot SLA breach refund for order " + orderId, legs);

            return Map.of(
                "status", "approved",
                "message", "AI-AUTOPILOT: Delivery SLA breach verified. Refund of $" + order.getTotalAmount() + " automatically approved and credited to your wallet.",
                "ticket_id", ticketId
            );
        }
        
        // Manual Admin HITL Path
        String ticketId = "HITL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HitlQueue ticket = HitlQueue.builder()
                .ticketId(ticketId)
                .type("refund_customer")
                .customer(customer)
                .orderId(order.getOrderId())
                .description("Refund request for order " + orderId + ". Reason: " + claimReason)
                .amount(order.getTotalAmount())
                .status("pending")
                .build();
        hitlQueuePort.save(ticket);
        
        return Map.of(
            "status", "pending_admin_approval",
            "message", "Refund filed. Awaiting manual Admin approval.",
            "ticket_id", ticketId
        );
    }


    @Override
    public List<Order> getCustomerOrders(String customerId) {
        return orderPort.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

}
