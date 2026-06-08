package ch.swissqcommerce.backend.domain.payment.adapter.in.web;

import ch.swissqcommerce.backend.domain.payment.core.model.Money;
import ch.swissqcommerce.backend.domain.payment.core.model.TransactionRecord;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentProcessingUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentProcessingUseCase paymentProcessingUseCase;

    @Data
    public static class ChargeRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        private BigDecimal amount;
        
        @NotBlank(message = "Currency is required")
        private String currency;
    }

    @PostMapping("/charge")
    public ResponseEntity<?> charge(@Valid @RequestBody ChargeRequest request) {
        try {
            Money money = new Money(request.getAmount(), request.getCurrency());
            TransactionRecord tx = paymentProcessingUseCase.processCharge(request.getOrderId(), money);
            return ResponseEntity.ok(tx);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{transactionId}/refund")
    public ResponseEntity<?> refund(@PathVariable String transactionId) {
        try {
            TransactionRecord tx = paymentProcessingUseCase.processRefund(transactionId);
            return ResponseEntity.ok(tx);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
