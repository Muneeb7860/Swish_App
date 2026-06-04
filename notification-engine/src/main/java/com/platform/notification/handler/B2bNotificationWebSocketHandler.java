package com.platform.notification.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class B2bNotificationWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(B2bNotificationWebSocketHandler.class);
    private static final String REDIS_CHANNEL_PREFIX = "notifications:b2b:";

    // Map of userId -> Active Sink
    private final Map<String, Sinks.Many<String>> activeUserSessions = new ConcurrentHashMap<>();
    
    private final ReactiveStringRedisTemplate redisTemplate;

    public B2bNotificationWebSocketHandler(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Extract userId from query params (e.g. ?userId=b2b-customer-123)
        // In a real OAuth2 scenario, extract this from the SecurityContext
        String query = session.getHandshakeInfo().getUri().getQuery();
        String userId = "anonymous";
        if (query != null && query.contains("userId=")) {
            userId = query.split("userId=")[1].split("&")[0];
        }

        log.info("WebSocket connection established for User: {}", userId);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        activeUserSessions.put(userId, sink);

        // Subscribe to this specific user's Redis channel
        redisTemplate.listenToChannel(REDIS_CHANNEL_PREFIX + userId)
            .map(message -> message.getMessage())
            .subscribe(msg -> sink.tryEmitNext(msg));

        // Send welcome
        sink.tryEmitNext("{\"type\":\"WELCOME\", \"message\":\"Connected securely to B2B Notification Engine\"}");

        Mono<Void> output = session.send(
            sink.asFlux().map(session::textMessage)
        );

        Mono<Void> input = session.receive().then();

        final String finalUserId = userId;
        return Mono.zip(input, output)
            .doFinally(signalType -> {
                log.info("WebSocket disconnected for User: {}", finalUserId);
                activeUserSessions.remove(finalUserId);
            })
            .then();
    }

    /**
     * Publishes a message to the Redis Backplane for a specific user.
     * This allows cross-node WebSocket messaging.
     */
    public Mono<Long> publishToUser(String userId, String payload) {
        log.debug("Publishing to Redis channel: {} payload: {}", REDIS_CHANNEL_PREFIX + userId, payload);
        return redisTemplate.convertAndSend(REDIS_CHANNEL_PREFIX + userId, payload);
    }
}
