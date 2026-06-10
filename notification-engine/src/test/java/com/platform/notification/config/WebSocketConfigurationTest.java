package com.platform.notification.config;

import com.platform.notification.handler.B2bNotificationWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class WebSocketConfigurationTest {

    @Mock
    private B2bNotificationWebSocketHandler webSocketHandler;

    @Test
    public void testWebSocketMapping() {
        WebSocketConfiguration config = new WebSocketConfiguration();
        HandlerMapping mapping = config.webSocketMapping(webSocketHandler);

        assertNotNull(mapping);
        assertTrue(mapping instanceof SimpleUrlHandlerMapping);
        
        SimpleUrlHandlerMapping urlMapping = (SimpleUrlHandlerMapping) mapping;
        assertEquals(-1, urlMapping.getOrder());
        
        Map<String, ?> urlMap = urlMapping.getUrlMap();
        assertTrue(urlMap.containsKey("/ws/notifications/b2b"));
        assertEquals(webSocketHandler, urlMap.get("/ws/notifications/b2b"));
    }

    @Test
    public void testHandlerAdapter() {
        WebSocketConfiguration config = new WebSocketConfiguration();
        WebSocketHandlerAdapter adapter = config.handlerAdapter();

        assertNotNull(adapter);
    }
}
