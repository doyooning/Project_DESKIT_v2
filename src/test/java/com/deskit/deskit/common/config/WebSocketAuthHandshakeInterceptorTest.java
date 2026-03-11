package com.deskit.deskit.common.config;

import com.deskit.deskit.account.jwt.JWTUtil;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WebSocketAuthHandshakeInterceptorTest {

    private JWTUtil jwtUtil;
    private WebSocketAuthHandshakeInterceptor interceptor;
    private WebSocketHandler wsHandler;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JWTUtil.class);
        interceptor = new WebSocketAuthHandshakeInterceptor(jwtUtil);
        wsHandler = mock(WebSocketHandler.class);
    }

    @Test
    void allowsSockJsInfoPathWithoutAuthProcessing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/info");
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowsOtherSockJsProbePathsWithoutAuthProcessing() {
        Map<String, Object> iframeAttrs = new HashMap<>();
        Map<String, Object> xhrAttrs = new HashMap<>();
        Map<String, Object> eventsourceAttrs = new HashMap<>();

        boolean iframe = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws/iframe/test")),
                mock(ServerHttpResponse.class),
                wsHandler,
                iframeAttrs
        );
        boolean xhr = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws/xhr_send")),
                mock(ServerHttpResponse.class),
                wsHandler,
                xhrAttrs
        );
        boolean eventsource = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws/eventsource")),
                mock(ServerHttpResponse.class),
                wsHandler,
                eventsourceAttrs
        );

        assertThat(iframe).isTrue();
        assertThat(xhr).isTrue();
        assertThat(eventsource).isTrue();
        assertThat(iframeAttrs).isEmpty();
        assertThat(xhrAttrs).isEmpty();
        assertThat(eventsourceAttrs).isEmpty();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowsWhenRequestIsNotServletRequest() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/ws/connect"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                wsHandler,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowsWhenTokenIsMissingOrBlank() {
        MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/ws/connect");
        missing.setCookies(new Cookie("other", "v"));
        Map<String, Object> firstAttrs = new HashMap<>();

        boolean first = interceptor.beforeHandshake(
                new ServletServerHttpRequest(missing),
                mock(ServerHttpResponse.class),
                wsHandler,
                firstAttrs
        );

        MockHttpServletRequest blank = new MockHttpServletRequest("GET", "/ws/connect");
        blank.addHeader("Authorization", "Bearer ");
        Map<String, Object> secondAttrs = new HashMap<>();

        boolean second = interceptor.beforeHandshake(
                new ServletServerHttpRequest(blank),
                mock(ServerHttpResponse.class),
                wsHandler,
                secondAttrs
        );

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(firstAttrs).isEmpty();
        assertThat(secondAttrs).isEmpty();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowsWhenHeadersDoNotContainUsableTokenAndCookiesAreNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/connect");
        request.addHeader("Authorization", "Token raw");
        request.addHeader("access", " ");
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowsWhenTokenExpired() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/connect");
        request.addHeader("Authorization", "Bearer token");
        when(jwtUtil.isExpired("token")).thenReturn(true);

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                new HashMap<>()
        );

        assertThat(result).isTrue();
        verify(jwtUtil).isExpired("token");
        verifyNoMoreInteractions(jwtUtil);
    }

    @Test
    void allowsWhenCategoryIsNotAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/connect");
        request.addHeader("Authorization", "Bearer token");
        when(jwtUtil.isExpired("token")).thenReturn(false);
        when(jwtUtil.getCategory("token")).thenReturn("refresh");

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                new HashMap<>()
        );

        assertThat(result).isTrue();
        verify(jwtUtil).isExpired("token");
        verify(jwtUtil).getCategory("token");
        verifyNoMoreInteractions(jwtUtil);
    }

    @Test
    void setsPrincipalAndRoleWhenLegacyHeaderTokenIsValid() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/connect");
        request.addHeader("access", "legacy-token");
        when(jwtUtil.isExpired("legacy-token")).thenReturn(false);
        when(jwtUtil.getCategory("legacy-token")).thenReturn("access");
        when(jwtUtil.getUsername("legacy-token")).thenReturn("user1");
        when(jwtUtil.getRole("legacy-token")).thenReturn("ROLE_USER");
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes.get("principal")).isInstanceOf(WebSocketPrincipal.class);
        assertThat(((WebSocketPrincipal) attributes.get("principal")).getName()).isEqualTo("user1");
        assertThat(attributes.get("role")).isEqualTo("ROLE_USER");
    }

    @Test
    void setsPrincipalAndRoleWhenCookieTokenIsValid() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/connect");
        request.setCookies(new Cookie("access", "cookie-token"));
        when(jwtUtil.isExpired("cookie-token")).thenReturn(false);
        when(jwtUtil.getCategory("cookie-token")).thenReturn("access");
        when(jwtUtil.getUsername("cookie-token")).thenReturn("cookie-user");
        when(jwtUtil.getRole("cookie-token")).thenReturn("ROLE_ADMIN");
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(((WebSocketPrincipal) attributes.get("principal")).getName()).isEqualTo("cookie-user");
        assertThat(attributes.get("role")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void swallowsJwtExceptionAndReturnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/connect");
        request.addHeader("Authorization", "Bearer boom");
        when(jwtUtil.isExpired("boom")).thenThrow(new RuntimeException("bad token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class),
                wsHandler,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
        verify(jwtUtil).isExpired("boom");
    }

    @Test
    void executesDebugLoggingBranchWhenDebugEnabled() {
        Logger logger = (Logger) LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);
        Level original = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            MockHttpServletRequest withCookies = new MockHttpServletRequest("GET", "/ws/connect");
            withCookies.setCookies(new Cookie("other", "v"));
            MockHttpServletRequest withoutCookies = new MockHttpServletRequest("GET", "/ws/connect");

            boolean first = interceptor.beforeHandshake(
                    new ServletServerHttpRequest(withCookies),
                    mock(ServerHttpResponse.class),
                    wsHandler,
                    new HashMap<>()
            );
            boolean second = interceptor.beforeHandshake(
                    new ServletServerHttpRequest(withoutCookies),
                    mock(ServerHttpResponse.class),
                    wsHandler,
                    new HashMap<>()
            );

            assertThat(first).isTrue();
            assertThat(second).isTrue();
            verifyNoInteractions(jwtUtil);
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    void afterHandshakeDoesNothing() {
        interceptor.afterHandshake(
                mock(ServerHttpRequest.class),
                mock(ServerHttpResponse.class),
                wsHandler,
                null
        );
    }
}
