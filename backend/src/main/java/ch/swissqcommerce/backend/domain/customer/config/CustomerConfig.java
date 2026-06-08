package ch.swissqcommerce.backend.domain.customer.config;

import ch.swissqcommerce.backend.domain.customer.core.service.CustomerServiceImpl;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerConfig {

    @Bean
    public CustomerUseCase customerUseCase(CustomerPort customerPort) {
        return new CustomerServiceImpl(customerPort);
    }
}
