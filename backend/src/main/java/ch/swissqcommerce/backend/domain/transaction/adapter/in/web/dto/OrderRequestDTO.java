package ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto;
import ch.swissqcommerce.backend.model.Customer;


import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderRequestDTO {
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotEmpty(message = "Cart items cannot be empty")
    private List<OrderUseCase.CartItem> items;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    private BigDecimal tipAmount = BigDecimal.ZERO;
    private Integer bagsReturned = 0;
}
