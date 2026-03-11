package com.deskit.deskit.account.service;

import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.AccessBlacklistRepository;
import com.deskit.deskit.account.repository.RefreshRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final AccessBlacklistRepository accessBlacklistRepository;

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        String refresh = extractRefreshToken(request);
        blacklistAccessToken(request);

        if (refresh != null) {
            refreshRepository.deleteByRefresh(refresh);
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        response.setHeader("access", "");
        response.addCookie(expireCookie("access"));
        response.addCookie(expireCookie("refresh"));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void blacklistAccessToken(HttpServletRequest request) {
        String access = extractAccessToken(request);
        if (access == null || access.isBlank()) {
            return;
        }

        try {
            if (jwtUtil.isExpired(access)) {
                return;
            }

            if (!"access".equals(jwtUtil.getCategory(access))) {
                return;
            }

            long ttlMs = jwtUtil.getRemainingMs(access);
            accessBlacklistRepository.blacklist(access, ttlMs);
        } catch (Exception ignored) {
            // Token cleanup should continue even when access token parsing fails.
        }
    }

    private String extractAccessToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }

        String legacy = request.getHeader("access");
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }

        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(c -> "access".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private Cookie expireCookie(String key) {
        Cookie cookie = new Cookie(key, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }
}
