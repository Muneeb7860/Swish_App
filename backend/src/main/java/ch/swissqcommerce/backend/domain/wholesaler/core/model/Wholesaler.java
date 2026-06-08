package ch.swissqcommerce.backend.domain.wholesaler.core.model;
import java.time.OffsetDateTime;


import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wholesaler {

    private String wholesalerId;

    private String name;

    @Builder.Default
    private Boolean isPrimary = true;

    @Builder.Default
    private Integer trustScore = 100;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean academyDiscountActive = false;

    @Builder.Default
    private BigDecimal baseInvoiceAmount = new BigDecimal("25.00");

    @Builder.Default
    private BigDecimal fallbackInvoiceAmount = new BigDecimal("35.00");

    private OffsetDateTime createdAt;
}