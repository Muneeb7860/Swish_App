package com.platform.notification.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.http.HttpHeaders;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class B2bNotificationWebSocketHandlerTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private B2bNotificationWebSocketHandler handler;

    @BeforeEach
    public void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        handler = new B2bNotificationWebSocketHandler(redisTemplate);
    }

    private WebSocketSession createMockSession(String userIdHeader, String queryParam) {
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);
        
        HttpHeaders headers = new HttpHeaders();
        if (userIdHeader != null) {
            headers.add("X-Authenticated-User", userIdHeader);
        }
        
        URI uri;
        try {
            if (queryParam != null) {
                uri = new URI("ws://localhost/ws?" + queryParam);
            } else {
                uri = new URI("ws://localhost/ws");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(handshakeInfo.getHeaders()).thenReturn(headers);
        when(handshakeInfo.getUri()).thenReturn(uri);
        when(handshakeInfo.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);

        // Mock textMessage creation
        when(session.textMessage(anyString())).thenAnswer(invocation -> {
            String payload = invocation.getArgument(0);
            WebSocketMessage msg = mock(WebSocketMessage.class);
            when(msg.getPayloadAsText()).thenReturn(payload);
            return msg;
        });

        // Mock close method
        when(session.close(any(CloseStatus.class))).thenReturn(Mono.empty());
        when(session.pingMessage(any())).thenReturn(mock(WebSocketMessage.class));

        return session;
    }

    @Test
    public void testRejectAnonymousOrMissingUser() {
        WebSocketSession session = createMockSession(null, null); // Anonymous / missing userId
        
        Mono<Void> result = handler.handle(session);
        
        StepVerifier.create(result)
                .verifyComplete();
        
        ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(statusCaptor.capture());
        assertEquals(4003, statusCaptor.getValue().getCode());
        assertEquals("Missing or invalid userId", statusCaptor.getValue().getReason());
    }

    @Test
    public void testSuccessfulConnectionAndMessageFlow() {
        WebSocketSession session = createMockSession("user-123", null);
        
        // Mock Redis listenToChannel
        ReactiveSubscription.Message<String, String> mockMessage = mock(ReactiveSubscription.Message.class);
        when(mockMessage.getMessage()).thenReturn("redis-payload");
        Flux<ReactiveSubscription.Message<String, String>> redisFlux = Flux.just(mockMessage);
        when((Flux) redisTemplate.listenToChannel("notifications:b2b:user-123")).thenReturn(redisFlux);
        
        // We want to capture the Flux of WebSocketMessage that the handler sends to session.send(...)
        ArgumentCaptor<Flux<WebSocketMessage>> fluxCaptor = ArgumentCaptor.forClass(Flux.class);
        when(session.send(fluxCaptor.capture())).thenReturn(Mono.empty());
        when(session.receive()).thenReturn(Flux.empty());

        Mono<Void> handleResult = handler.handle(session);

        StepVerifier.create(handleResult).verifyComplete();

        // Verify that send was called
        verify(session).send(any());
        
        // Assert that redis message and WELCOME message are emitted
        Flux<WebSocketMessage> outputFlux = fluxCaptor.getValue();
        StepVerifier.create(outputFlux)
                .assertNext(msg -> {
                    String payload = msg.getPayloadAsText();
                    assertEquals("redis-payload", payload);
                })
                .assertNext(msg -> {
                    String payload = msg.getPayloadAsText();
                    assertTrue(payload.contains("WELCOME"));
                    assertTrue(payload.contains("Connected securely to Hardened Notification Engine v2"));
                })
                .thenCancel()
                .verify();
    }

    @Test
    public void testConnectionLimitEvictsOldest() {
        String userId = "user-cap";
        
        // Prepare mock Redis template
        Flux<ReactiveSubscription.Message<String, String>> emptyFlux = Flux.empty();
        when((Flux) redisTemplate.listenToChannel(anyString())).thenReturn(emptyFlux);

        List<WebSocketSession> sessions = new ArrayList<>();
        List<Disposable> disposables = new ArrayList<>();

        try {
            // Create 6 sessions
            for (int i = 0; i < 6; i++) {
                WebSocketSession session = createMockSession(userId, null);
                // We want session.receive() to never complete during the test to simulate open connections
                when(session.receive()).thenReturn(Flux.never());
                
                // Capture send so we can prevent it from terminating or complete immediately
                when(session.send(any())).thenReturn(Mono.never());
                
                sessions.add(session);
                
                // Call handle on the handler to register the session
                Mono<Void> handleResult = handler.handle(session);
                // Subscribe so that the session gets registered in the handler
                disposables.add(handleResult.subscribe());
            }

            // The first session (index 0) should have been evicted when the 6th session (index 5) was added.
            // Let's verify that session.close(new CloseStatus(4001, "TOO_MANY_CONNECTIONS")) was called on session 0.
            ArgumentCaptor<CloseStatus> statusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
            verify(sessions.get(0)).close(statusCaptor.capture());
            
            CloseStatus closedStatus = statusCaptor.getValue();
            assertEquals(4001, closedStatus.getCode());
            assertEquals("TOO_MANY_CONNECTIONS", closedStatus.getReason());

            // The rest of the sessions (1 to 5) should NOT have close called with 4001
            for (int i = 1; i < 6; i++) {
                verify(sessions.get(i), never()).close(any(CloseStatus.class));
            }
        } finally {
            for (Disposable d : disposables) {
                d.dispose();
            }
        }
    }

    @Test
    public void testPublishToUser() {
        when(redisTemplate.convertAndSend("notifications:b2b:user-1", "test-payload"))
                .thenReturn(Mono.just(1L));

        Mono<Long> result = handler.publishToUser("user-1", "test-payload");
        
        StepVerifier.create(result)
                .expectNext(1L)
                .verifyComplete();
                
        verify(redisTemplate).convertAndSend("notifications:b2b:user-1", "test-payload");
    }

    @Test
    public void testFallbackUserIdFromQueryParam() {
        WebSocketSession session = createMockSession(null, "userId=user-query&other=val");
        
        // Mock Redis
        ReactiveSubscription.Message<String, String> mockMessage = mock(ReactiveSubscription.Message.class);
        when(mockMessage.getMessage()).thenReturn("redis-payload");
        when((Flux) redisTemplate.listenToChannel("notifications:b2b:user-query")).thenReturn(Flux.empty());
        
        when(session.send(any())).thenReturn(Mono.empty());
        when(session.receive()).thenReturn(Flux.empty());

        Mono<Void> handleResult = handler.handle(session);

        StepVerifier.create(handleResult).verifyComplete();

        // Verify that Redis subscription occurred with the query param user ID
        verify(redisTemplate).listenToChannel("notifications:b2b:user-query");
    }
}
