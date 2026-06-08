package ch.swissqcommerce.backend.domain.payment.adapter.in.web.dto;
import ch.swissqcommerce.backend.model.Customer;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequestDTO {

    @NotNull(message = "Order ID is required")
    private Integer orderId;

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}
