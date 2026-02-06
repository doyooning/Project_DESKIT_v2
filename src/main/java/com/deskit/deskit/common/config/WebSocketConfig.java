package com.deskit.deskit.common.config;

import com.deskit.deskit.account.jwt.JWTUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private final JWTUtil jwtUtil;

    public WebSocketConfig(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/sub");
        config.setApplicationDestinationPrefixes("/app", "/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-public")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/ws","/api/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor(jwtUtil))
                .setHandshakeHandler(new WebSocketAuthHandshakeHandler());

        registry.addEndpoint("/api/ws-public")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // 여긴 withSockJS() 붙이지 말고 "진짜 websocket"만
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (accessor.getCommand() == StompCommand.CONNECT) {
                    if (accessor.getUser() == null && accessor.getSessionAttributes() != null) {
                        Object principal = accessor.getSessionAttributes().get("principal");
                        if (principal instanceof java.security.Principal) {
                            accessor.setUser((java.security.Principal) principal);
                        }
                    }

                    String role = null;
                    if (accessor.getSessionAttributes() != null) {
                        Object storedRole = accessor.getSessionAttributes().get("role");
                        if (storedRole instanceof String) {
                            role = (String) storedRole;
                        }
                    }

                    if (role == null) {
                        String token = resolveToken(accessor);
                        if (token == null || token.isBlank()) {
                            if (accessor.getUser() != null) {
                                log.debug("stomp.connect principal={} role=unknown", accessor.getUser().getName());
                            } else {
                                log.debug("stomp.connect no token");
                            }
                            return message;
                        }
                        try {
                            if (jwtUtil.isExpired(token)) {
                                log.debug("stomp.connect token expired");
                                return message;
                            }
                        } catch (ExpiredJwtException ex) {
                            log.debug("stomp.connect token expired");
                            return message;
                        } catch (Exception ex) {
                            log.debug("stomp.connect token parse failed: {}", ex.getMessage());
                            return message;
                        }

                        String category = jwtUtil.getCategory(token);
                        if (!"access".equals(category)) {
                            log.debug("stomp.connect token category invalid");
                            return message;
                        }

                        String username = jwtUtil.getUsername(token);
                        role = jwtUtil.getRole(token);
                        if (accessor.getUser() == null) {
                            accessor.setUser(new WebSocketPrincipal(username));
                        }
                        if (accessor.getSessionAttributes() != null) {
                            accessor.getSessionAttributes().put("role", role);
                        }
                    }

                    if (accessor.getUser() != null) {
                        log.debug("stomp.connect principal={} role={}", accessor.getUser().getName(), role);
                    }
                }
                return message;
            }
        });
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        String legacy = accessor.getFirstNativeHeader("access");
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        return null;
    }
}
