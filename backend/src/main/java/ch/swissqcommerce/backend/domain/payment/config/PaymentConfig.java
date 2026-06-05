package ch.swissqcommerce.backend.domain.payment.config;

import ch.swissqcommerce.backend.domain.payment.core.service.PaymentServiceImpl;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.OutboxEventPort;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PaymentConfig {

    @Bean
    public PaymentUseCase paymentUseCase(PaymentPort paymentPort,
                                       OrderPort orderPort,
                                       LedgerUseCase ledgerUseCase,
                                       OutboxEventPort outboxEventPort,
                                       ApplicationEventPublisher eventPublisher,
                                       StringRedisTemplate redisTemplate) {
        return new PaymentServiceImpl(
                paymentPort,
                orderPort,
                ledgerUseCase,
                outboxEventPort,
                eventPublisher,
                redisTemplate
        );
    }
}
