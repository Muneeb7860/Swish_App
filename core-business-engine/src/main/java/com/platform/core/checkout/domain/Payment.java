package com.platform.core.checkout.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Default constructor for JPA
    public Payment() {}

    public Payment(String idempotencyKey, String customerId, String orderId, BigDecimal amount, PaymentStatus status) {
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCustomerId() { return customerId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
}
