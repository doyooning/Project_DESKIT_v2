package com.deskit.deskit.account.service;

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

    private final RefreshRepository refreshRepository;

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        String refresh = extractRefreshToken(request);

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

    private Cookie expireCookie(String key) {
        Cookie cookie = new Cookie(key, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }
}

