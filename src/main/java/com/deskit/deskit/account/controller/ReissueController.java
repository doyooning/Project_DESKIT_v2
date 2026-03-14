package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.RefreshRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequiredArgsConstructor
public class ReissueController {
    private static final long ACCESS_TTL_MS = 600_000L;
    private static final long REFRESH_TTL_MS = 86_400_000L;

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/api/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refresh = extractRefreshToken(request);
        if (refresh == null || refresh.isBlank()) {
            log.info("refresh token is null");
            return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
        }

        final String username;
        final String role;
        try {
            jwtUtil.isExpired(refresh);

            String category = jwtUtil.getCategory(refresh);
            if (!"refresh".equals(category)) {
                log.info("refresh token is invalid");
                return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
            }

            username = jwtUtil.getUsername(refresh);
            role = jwtUtil.getRole(refresh);
        } catch (ExpiredJwtException e) {
            log.info("refresh token is expired");
            return new ResponseEntity<>("refresh token expired", HttpStatus.BAD_REQUEST);
        } catch (JwtException | IllegalArgumentException e) {
            log.info("refresh token is invalid");
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }

        String newAccess = jwtUtil.createJwt("access", username, role, ACCESS_TTL_MS);
        String newRefresh = jwtUtil.createJwt("refresh", username, role, REFRESH_TTL_MS);

        boolean rotated = refreshRepository.rotate(refresh, username, newRefresh, REFRESH_TTL_MS);
        if (!rotated) {
            log.info("refresh token does not exist in store");
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }

        response.setHeader("access", newAccess);
        response.addCookie(createCookie("access", newAccess, (int) (ACCESS_TTL_MS / 1000)));
        response.addCookie(createCookie("refresh", newRefresh, (int) (REFRESH_TTL_MS / 1000)));

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("refresh".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Cookie createCookie(String key, String value, int maxAge) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }
}
