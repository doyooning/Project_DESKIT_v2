package com.deskit.deskit.account.jwt;

import com.deskit.deskit.account.repository.AccessBlacklistRepository;
import com.deskit.deskit.account.repository.RefreshRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

public class CustomLogoutFilter extends GenericFilterBean {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final AccessBlacklistRepository accessBlacklistRepository;
    private final boolean cookieSecure;

    public CustomLogoutFilter(JWTUtil jwtUtil,
                              RefreshRepository refreshRepository,
                              AccessBlacklistRepository accessBlacklistRepository,
                              boolean cookieSecure) {

        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.accessBlacklistRepository = accessBlacklistRepository;
        this.cookieSecure = cookieSecure;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        doFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
    }

    private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        //path and method verify
        String requestUri = request.getRequestURI();
        if (!requestUri.matches("^\\/logout$")) {

            filterChain.doFilter(request, response);
            return;
        }
        String requestMethod = request.getMethod();
        if (!requestMethod.equals("POST")) {

            filterChain.doFilter(request, response);
            return;
        }

        blacklistAccessTokenIfPossible(request);

        //get refresh token
        String refresh = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("refresh")) {
                    refresh = cookie.getValue();
                }
            }
        }

        //refresh null check
        if (refresh == null) {

            clearSessionAndCookies(request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        //expired check
        try {
            jwtUtil.isExpired(refresh);
        } catch (ExpiredJwtException e) {

            //response status code
            clearSessionAndCookies(request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // 토큰이 refresh인지 확인 (발급시 페이로드에 명시)
        String category = jwtUtil.getCategory(refresh);
        if (!category.equals("refresh")) {

            //response status code
            clearSessionAndCookies(request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        //DB에 저장되어 있는지 확인
        Boolean isExist = refreshRepository.existsByRefresh(refresh);
        if (!isExist) {

            //response status code
            clearSessionAndCookies(request, response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        //로그아웃 진행
        //Refresh 토큰 DB에서 제거
        refreshRepository.deleteByRefresh(refresh);

        clearSessionAndCookies(request, response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void blacklistAccessTokenIfPossible(HttpServletRequest request) {
        String access = resolveAccessToken(request);
        if (access == null || access.isBlank()) {
            return;
        }

        try {
            if (jwtUtil.isExpired(access)) {
                return;
            }

            String category = jwtUtil.getCategory(access);
            if (!"access".equals(category)) {
                return;
            }

            long ttlMs = jwtUtil.getRemainingMs(access);
            accessBlacklistRepository.blacklist(access, ttlMs);
        } catch (Exception ignored) {
            // Logout should always continue even when access token parsing fails.
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }

        String legacy = request.getHeader("access");
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("access".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearSessionAndCookies(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Cookie cookie = new Cookie("refresh", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setSecure(cookieSecure);

        Cookie accessCookie = new Cookie("access", null);
        accessCookie.setMaxAge(0);
        accessCookie.setPath("/");
        accessCookie.setSecure(cookieSecure);

        response.setHeader("access", "");
        response.addCookie(cookie);
        response.addCookie(accessCookie);
    }
}
