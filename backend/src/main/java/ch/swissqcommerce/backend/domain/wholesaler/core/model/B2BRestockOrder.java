package ch.swissqcommerce.backend.domain.wholesaler.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import ch.swissqcommerce.backend.model.DarkStore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "b2b_restock_orders", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2BRestockOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restock_order_id")
    private Integer restockOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private DarkStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wholesaler_id")
    private Wholesaler wholesaler;

    @Column(name = "invoice_amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal invoiceAmount;

    @Column(name = "is_fallback", nullable = false)
    @Builder.Default
    private Boolean isFallback = false;

    @Column(name = "status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "idempotency_key", length = 100, unique = true)
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
