package ch.swissqcommerce.backend.domain.enrollment.config;

import ch.swissqcommerce.backend.domain.enrollment.core.service.RiderServiceImpl;
import ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnrollmentConfig {

    @Bean
    public RiderUseCase riderUseCase(EnrollmentOutPort outPort) {
        return new RiderServiceImpl(outPort);
    }
}
