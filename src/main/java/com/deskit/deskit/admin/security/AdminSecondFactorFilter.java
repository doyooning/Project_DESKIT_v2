package com.deskit.deskit.admin.security;

import com.deskit.deskit.admin.service.AdminAuthSessionKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AdminSecondFactorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!requiresSecondFactorCheck(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!isAdmin) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Object pendingLoginId = session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID);
        Object verified = session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED);
        boolean isVerified = Boolean.TRUE.equals(verified);

        if (pendingLoginId != null && !isVerified) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"관리자 인증이 필요합니다.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresSecondFactorCheck(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/admin/auth")) {
            return false;
        }
        return path.startsWith("/api/admin") || "/my".equals(path);
    }
}
