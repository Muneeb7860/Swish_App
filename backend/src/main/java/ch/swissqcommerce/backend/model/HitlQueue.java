package ch.swissqcommerce.backend.model;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "hitl_queue", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HitlQueue {

    @Id
    @Column(name = "ticket_id", length = 50)
    @Size(max = 50)
    private String ticketId;

    @Column(name = "type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String description;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    @Column(name = "status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

