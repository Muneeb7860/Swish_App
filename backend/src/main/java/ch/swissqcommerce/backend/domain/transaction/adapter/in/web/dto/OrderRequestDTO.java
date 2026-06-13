package ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto;

import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class OrderRequestDTO {
    // Optional in the request body: when omitted, the controller resolves the
    // customer from the authenticated JWT subject (a caller checks out for
    // themselves). An explicit value is still honored for admin-on-behalf flows,
    // subject to the controller's ownership/IDOR guard.
    private String customerId;

    @NotEmpty(message = "Cart items cannot be empty")
    private List<OrderUseCase.CartItem> items;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    private BigDecimal tipAmount = BigDecimal.ZERO;
    private Integer bagsReturned = 0;
}
