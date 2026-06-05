package com.platform.core.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
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
