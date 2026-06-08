package ch.swissqcommerce.backend.domain.payment.port.in;

import ch.swissqcommerce.backend.domain.payment.core.model.Money;
import ch.swissqcommerce.backend.domain.payment.core.model.TransactionRecord;

public interface PaymentProcessingUseCase {
    TransactionRecord processCharge(String orderId, Money amount);
    TransactionRecord processRefund(String transactionId);
}
