package com.platform.core.inventory;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory_items")
class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;
    
    // Running total, maintained by DB trigger, not by application logic
    @Column(nullable = false)
    private Integer stockLevel = 0;

    // Getters
    public Long getId() { return id; }
    public String getSku() { return sku; }
    public Integer getStockLevel() { return stockLevel; }
}

@Entity
@Table(name = "inventory_transactions")
class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantityDelta;

    @Column(nullable = false)
    private String referenceId; // orderId or stock-take ID

    private Instant createdAt = Instant.now();

    public InventoryTransaction() {}

    public InventoryTransaction(String sku, Integer quantityDelta, String referenceId) {
        this.sku = sku;
        this.quantityDelta = quantityDelta;
        this.referenceId = referenceId;
    }
}
