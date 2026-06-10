package ch.swissqcommerce.backend.domain.inventory.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the hexagonal inventory domain, backed by the oltp.inventory_items table
 * (created in V21__add_missing_event_inventory_tables.sql).
 */
@Entity
@Table(name = "inventory_items", schema = "oltp")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemEntity {

    @Id
    @Column(nullable = false, length = 255)
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String sku;

    @Column(name = "available_amount", nullable = false)
    private int availableAmount;

    @Column(name = "reserved_amount", nullable = false)
    private int reservedAmount;

    /** Optimistic locking — prevents lost-update races on concurrent reserve/release. */
    @Version
    private Long version;
}
