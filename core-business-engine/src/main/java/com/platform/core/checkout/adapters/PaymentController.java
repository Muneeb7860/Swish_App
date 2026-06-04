package com.platform.core.checkout.adapters;

import com.platform.core.checkout.domain.Payment;
import com.platform.core.checkout.domain.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/checkout")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Payment> createPayment(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PaymentRequest request) {
        
        if (idempotencyKey == null) {
            return ResponseEntity.badRequest().build();
        }

        Payment payment = paymentService.processCheckoutPayment(
                idempotencyKey,
                request.customerId(),
                request.orderId(),
                request.amount()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }
}

record PaymentRequest(String customerId, String orderId, BigDecimal amount) {}
