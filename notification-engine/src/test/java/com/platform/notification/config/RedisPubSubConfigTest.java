package com.platform.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

public class RedisPubSubConfigTest {

    @Test
    public void testReactiveStringRedisTemplate() {
        RedisPubSubConfig config = new RedisPubSubConfig();
        ReactiveRedisConnectionFactory factory = mock(ReactiveRedisConnectionFactory.class);
        
        org.springframework.data.redis.core.ReactiveStringRedisTemplate template = config.reactiveStringRedisTemplate(factory);
        assertNotNull(template);
    }
}
