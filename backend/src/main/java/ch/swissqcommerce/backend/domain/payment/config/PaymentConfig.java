package ch.swissqcommerce.backend.domain.payment.config;

import ch.swissqcommerce.backend.domain.payment.core.service.PaymentServiceImpl;
import ch.swissqcommerce.backend.domain.payment.core.service.PaymentUseCaseImpl;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentProcessingUseCase;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import ch.swissqcommerce.backend.domain.payment.port.out.OrderValidationPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentEventPublisherPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentGatewayPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentLedgerPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import ch.swissqcommerce.backend.domain.payment.port.out.TransactionRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Bean
    public PaymentProcessingUseCase paymentProcessingUseCase(
            TransactionRepositoryPort transactionRepositoryPort,
            PaymentGatewayPort paymentGatewayPort) {
        return new PaymentServiceImpl(transactionRepositoryPort, paymentGatewayPort);
    }

    @Bean
    public PaymentUseCase paymentUseCase(
            PaymentPort paymentPort,
            OrderValidationPort orderValidationPort,
            PaymentLedgerPort paymentLedgerPort,
            PaymentEventPublisherPort paymentEventPublisherPort) {
        return new PaymentUseCaseImpl(
                paymentPort, orderValidationPort, paymentLedgerPort, paymentEventPublisherPort);
    }
}
