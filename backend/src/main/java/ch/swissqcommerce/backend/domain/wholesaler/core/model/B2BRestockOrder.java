package ch.swissqcommerce.backend.domain.wholesaler.core.model;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import ch.swissqcommerce.backend.model.DarkStore;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BRestockOrder {

    private Integer restockOrderId;

    private DarkStore store;

    private Wholesaler wholesaler;

    private BigDecimal invoiceAmount;

    @Builder.Default
    private Boolean isFallback = false;

    private String status = "pending";

    private String idempotencyKey;

    private OffsetDateTime createdAt;
}