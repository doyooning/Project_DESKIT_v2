package com.deskit.deskit.account.jwt;

import com.deskit.deskit.account.dto.UserDTO;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.AccessBlacklistRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Log4j2
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    private final AccessBlacklistRepository accessBlacklistRepository;

    public JWTFilter(JWTUtil jwtUtil, AccessBlacklistRepository accessBlacklistRepository) {
        this.jwtUtil = jwtUtil;
        this.accessBlacklistRepository = accessBlacklistRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = resolveToken(request);

        if (accessToken == null || accessToken.isBlank()) {
            log.info("access token is null");
            filterChain.doFilter(request, response);
            return;
        }

        if (accessBlacklistRepository.isBlacklisted(accessToken)) {
            log.info("blacklisted access token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            jwtUtil.isExpired(accessToken);

            String category = jwtUtil.getCategory(accessToken);
            if (!"access".equals(category)) {
                log.info("access token is not access");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String username = jwtUtil.getUsername(accessToken);
            String role = jwtUtil.getRole(accessToken);
            log.info("username {} role {}", username, role);

            UserDTO userDTO = UserDTO.builder()
                    .username(username)
                    .role(role)
                    .newUser("ROLE_GUEST".equals(role))
                    .name(jwtUtil.getName(accessToken))
                    .email(jwtUtil.getEmail(accessToken))
                    .profileUrl(jwtUtil.getProfileUrl(accessToken))
                    .build();

            CustomOAuth2User customOAuth2User = new CustomOAuth2User(userDTO);
            Authentication authToken = new UsernamePasswordAuthenticationToken(customOAuth2User, null, customOAuth2User.getAuthorities());
            log.info("auth token {}", authToken);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (ExpiredJwtException e) {
            log.info("expired token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.info("invalid access token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            log.info("Bearer token found");
            return auth.substring(7);
        }

        String legacy = request.getHeader("access");
        if (legacy != null && !legacy.isBlank()) {
            log.info("legacy token found");
            return legacy;
        }

        if (request.getCookies() == null) {
            log.info("cookie is null");
            return null;
        }

        for (Cookie c : request.getCookies()) {
            log.info("cookie found: {}", c.getName());
            if ("access".equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String uri = request.getRequestURI();

        boolean isOauthOrLogin = servletPath.startsWith("/oauth2/")
                || servletPath.startsWith("/login/oauth2/")
                || servletPath.startsWith("/login");
        boolean isWs = servletPath.startsWith("/ws/")
                || servletPath.startsWith("/ws")
                || servletPath.startsWith("/api/ws");

        boolean isTestAuth = servletPath.startsWith("/api/internal/test-auth/")
                || servletPath.startsWith("/internal/test-auth/")
                || uri.contains("/api/internal/test-auth/")
                || uri.contains("/internal/test-auth/");
        boolean isActuator = servletPath.startsWith("/actuator/")
                || "/actuator".equals(servletPath)
                || uri.startsWith("/actuator/")
                || "/actuator".equals(uri);

        return isOauthOrLogin || isWs || isTestAuth || isActuator;
    }
}
