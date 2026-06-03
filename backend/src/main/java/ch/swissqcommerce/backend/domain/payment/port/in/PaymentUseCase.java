package ch.swissqcommerce.backend.domain.payment.port.in;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentUseCase {

    Payment authorizePayment(Integer orderId,
                             String customerId,
                             BigDecimal amount,
                             String paymentMethod,
                             String idempotencyKey);

    Payment capturePayment(Integer paymentId);

    Payment getPayment(Integer paymentId);

    List<Payment> getPaymentsByCustomer(String customerId);
}
