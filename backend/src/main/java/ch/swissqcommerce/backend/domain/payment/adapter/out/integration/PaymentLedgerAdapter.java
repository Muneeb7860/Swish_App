package ch.swissqcommerce.backend.domain.payment.adapter.out.integration;

import ch.swissqcommerce.backend.domain.payment.port.out.PaymentLedgerPort;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentLedgerAdapter implements PaymentLedgerPort {

    private final LedgerUseCase ledgerUseCase;

    public PaymentLedgerAdapter(LedgerUseCase ledgerUseCase) {
        this.ledgerUseCase = ledgerUseCase;
    }

    @Override
    public void recordPaymentAuth(Integer orderId, String customerId, BigDecimal amount) {
        ledgerUseCase.recordTransaction(
                "PAYMENT-AUTH",
                "Authorise payment for order " + orderId,
                List.of(
                        new LedgerUseCase.LedgerLeg(
                                "customer", customerId, amount, BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, amount)));
    }
}
