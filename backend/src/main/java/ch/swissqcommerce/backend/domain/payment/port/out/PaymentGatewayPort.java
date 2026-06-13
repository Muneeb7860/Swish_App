package ch.swissqcommerce.backend.domain.payment.port.out;

import ch.swissqcommerce.backend.domain.payment.core.model.Money;

public interface PaymentGatewayPort {
    /** Charges the payment and returns the gateway reference ID if successful. */
    String authorizeAndCapture(String orderId, Money amount);

    /** Refunds the transaction and returns a boolean indicating success. */
    boolean refund(String gatewayReference);
}
