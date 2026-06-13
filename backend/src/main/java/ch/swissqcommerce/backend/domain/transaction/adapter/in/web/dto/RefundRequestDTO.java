package ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class RefundRequestDTO {
    @NotBlank(message = "Reason is required")
    private String claimReason;

    private BigDecimal customerLatitude;
    private BigDecimal customerLongitude;
}
