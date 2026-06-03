package ch.swissqcommerce.backend.domain.payment.adapter.in.web;

import ch.swissqcommerce.backend.domain.payment.adapter.in.web.dto.PaymentRequestDTO;
import ch.swissqcommerce.backend.domain.payment.adapter.in.web.dto.PaymentResponseDTO;
import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentUseCase paymentUseCase;

    @PostMapping
    public ResponseEntity<?> createPayment(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequestDTO request) {
        try {
            Payment payment = paymentUseCase.authorizePayment(
                    request.getOrderId(),
                    request.getCustomerId(),
                    request.getAmount(),
                    request.getPaymentMethod(),
                    idempotencyKey
            );
            return ResponseEntity.status(201).body(mapToDTO(payment));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<?> capturePayment(@PathVariable Integer paymentId) {
        try {
            Payment payment = paymentUseCase.capturePayment(paymentId);
            return ResponseEntity.ok(mapToDTO(payment));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPaymentById(@PathVariable Integer paymentId) {
        try {
            Payment payment = paymentUseCase.getPayment(paymentId);
            return ResponseEntity.ok(mapToDTO(payment));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listPayments(@RequestParam String customerId) {
        try {
            List<Payment> payments = paymentUseCase.getPaymentsByCustomer(customerId);
            List<PaymentResponseDTO> response = payments.stream().map(this::mapToDTO).collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    private PaymentResponseDTO mapToDTO(Payment payment) {
        return PaymentResponseDTO.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrder() != null ? payment.getOrder().getOrderId() : null)
                .customerId(payment.getCustomer() != null ? payment.getCustomer().getCustomerId() : null)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .idempotencyKey(payment.getIdempotencyKey())
                .externalReference(payment.getExternalReference())
                .createdAt(payment.getCreatedAt())
                .capturedAt(payment.getCapturedAt())
                .refundedAt(payment.getRefundedAt())
                .build();
    }
}
