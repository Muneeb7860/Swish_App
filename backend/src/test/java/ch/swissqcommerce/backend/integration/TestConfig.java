package ch.swissqcommerce.backend.integration;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Test configuration providing mock beans to satisfy Redis and Kafka dependencies during integration tests.
 */
@Configuration
public class TestConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, String> kafkaTemplate() {
        return Mockito.mock(KafkaTemplate.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate mockTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Mockito.when(mockTemplate.opsForValue()).thenReturn(mockOps);
        return mockTemplate;
    }

}
