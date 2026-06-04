package com.platform.core.checkout.adapters;

import com.platform.core.checkout.domain.Payment;
import com.platform.core.checkout.domain.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/checkout")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/intents")
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PaymentRequest request) {
        
        if (idempotencyKey == null) {
            return ResponseEntity.badRequest().build();
        }

        // 1. Persist the pending payment internally
        Payment payment = paymentService.processCheckoutPayment(
                idempotencyKey,
                request.customerId(),
                request.orderId(),
                request.amount()
        );

        // 2. Generate Mock Stripe Client Secret
        String mockClientSecret = "pi_mock_" + payment.getId() + "_secret_123456789";

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new PaymentIntentResponse(payment.getId().toString(), mockClientSecret, "requires_payment_method")
        );
    }
}

record PaymentRequest(String customerId, String orderId, BigDecimal amount) {}
record PaymentIntentResponse(String paymentId, String clientSecret, String status) {}
