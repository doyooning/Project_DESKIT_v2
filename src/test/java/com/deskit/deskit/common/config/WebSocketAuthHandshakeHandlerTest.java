package com.deskit.deskit.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketAuthHandshakeHandlerTest {

    private final WebSocketAuthHandshakeHandler handler = new WebSocketAuthHandshakeHandler();

    @Test
    void returnsPrincipalFromAttributesWhenPresent() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Principal principal = new WebSocketPrincipal("user1");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("principal", principal);

        Principal resolved = handler.determineUser(request, wsHandler, attributes);

        assertThat(resolved).isSameAs(principal);
    }

    @Test
    void fallsBackToDefaultWhenNoPrincipalInAttributes() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("principal", "not-a-principal");

        Principal resolved = handler.determineUser(request, wsHandler, attributes);

        assertThat(resolved).isNull();
    }
}
