package com.deskit.deskit.common.config;

import com.deskit.deskit.account.jwt.JWTUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);
    private final JWTUtil jwtUtil;

    public WebSocketAuthHandshakeInterceptor(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String path = request.getURI().getPath();

        // ✅ SockJS 내부 엔드포인트는 무조건 패스 (폭주 + OOM 방지)
        if (path.endsWith("/info")
                || path.contains("/iframe")
                || path.contains("/xhr")
                || path.contains("/eventsource")) {
            return true;
        }

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // ✅ 로그 최소화
        if (log.isDebugEnabled()) {
            Cookie[] cookies = httpRequest.getCookies();
            log.debug("ws.handshake path={} cookieCount={}", path, cookies == null ? 0 : cookies.length);
        }

        String token = resolveToken(httpRequest);
        if (token == null || token.isBlank()) {
            return true;
        }

        try {
            if (jwtUtil.isExpired(token)) return true;

            String category = jwtUtil.getCategory(token);
            if (!"access".equals(category)) return true;

            String username = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);

            attributes.put("principal", new WebSocketPrincipal(username));
            attributes.put("role", role);
        } catch (Exception e) {
            // 예외 발생 시 로그만 찍고 연결은 허용(또는 거부)하여 500 에러 방지
            log.error("WebSocket Token Error: {}", e.getMessage());
            return true; // 혹은 false로 리턴하여 연결 거부
        }

//        String category = jwtUtil.getCategory(token);
//        if (!"access".equals(category)) return true;
//
//        String username = jwtUtil.getUsername(token);
//        String role = jwtUtil.getRole(token);
//
//        attributes.put("principal", new WebSocketPrincipal(username));
//        attributes.put("role", role);
//
//        if (log.isDebugEnabled()) {
//            log.debug("ws.handshake principal set username={} role={}", username, role);
//        }

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {
    }

    // =========================
    // 토큰 추출
    // =========================
    private String resolveToken(HttpServletRequest request) {
        // 1) Authorization: Bearer xxx
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }

        // 2) legacy header
        String legacy = request.getHeader("access");
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }

        // 3) cookie
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if ("access".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}


