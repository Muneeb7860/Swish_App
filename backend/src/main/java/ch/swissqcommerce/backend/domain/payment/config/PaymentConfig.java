package ch.swissqcommerce.backend.domain.payment.config;

import ch.swissqcommerce.backend.domain.payment.core.service.PaymentServiceImpl;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.OutboxEventPort;
import ch.swissqcommerce.backend.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PaymentConfig {

    @Bean
    public PaymentUseCase paymentUseCase(PaymentPort paymentPort,
                                       OrderRepository orderRepository,
                                       LedgerUseCase ledgerUseCase,
                                       OutboxEventPort outboxEventPort,
                                       ApplicationEventPublisher eventPublisher,
                                       StringRedisTemplate redisTemplate) {
        return new PaymentServiceImpl(
                paymentPort,
                orderRepository,
                ledgerUseCase,
                outboxEventPort,
                eventPublisher,
                redisTemplate
        );
    }
}
