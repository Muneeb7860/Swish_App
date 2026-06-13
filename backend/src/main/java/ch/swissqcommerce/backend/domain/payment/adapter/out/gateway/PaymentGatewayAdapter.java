package ch.swissqcommerce.backend.domain.payment.adapter.out.gateway;

import ch.swissqcommerce.backend.domain.payment.core.model.Money;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentGatewayPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public String authorizeAndCapture(String orderId, Money amount) {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean refund(String gatewayReference) {
        return true;
    }
}
