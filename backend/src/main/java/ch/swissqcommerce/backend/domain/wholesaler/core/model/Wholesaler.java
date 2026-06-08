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

    private Boolean isPrimary = true;

    private Integer trustScore = 100;

    private Boolean isActive = true;

    private Boolean academyDiscountActive = false;

    private BigDecimal baseInvoiceAmount = new BigDecimal("25.00");

    private BigDecimal fallbackInvoiceAmount = new BigDecimal("35.00");

    private OffsetDateTime createdAt;
}