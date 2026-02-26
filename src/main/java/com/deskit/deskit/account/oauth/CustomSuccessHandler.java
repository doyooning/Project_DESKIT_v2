package com.deskit.deskit.account.oauth;


import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.RefreshRepository;
import com.deskit.deskit.admin.entity.Admin;
import com.deskit.deskit.admin.repository.AdminRepository;
import com.deskit.deskit.admin.service.AdminAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Log4j2
@Component
@RequiredArgsConstructor
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final AdminRepository adminRepository;
    private final AdminAuthService adminAuthService;
    @Value("${app.web-base-url:http://localhost:5173}")
    private String webBaseUrl;
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        CustomOAuth2User user = (CustomOAuth2User) authentication.getPrincipal();
        String username = user.getUsername();
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        // Redirect pending signup users without issuing tokens.
        if (user.isNewUser()) {
            // Short-lived signup token to authorize the signup API calls.
            String signupToken = jwtUtil.createSignupJwt(
                    username,
                    role,
                    user.getName(),
                    user.getEmail(),
                    user.getProfileUrl(),
                    300000L
            );
            response.setHeader("access", signupToken);
            response.addCookie(createCookie("access", signupToken, Math.toIntExact(300000L / 1000)));
            // Encoded token for safe query parameter transport.
            String encodedToken = URLEncoder.encode(signupToken, StandardCharsets.UTF_8);
            response.sendRedirect(buildWebUrl("/signup?token=" + encodedToken));
            return;
        }

        if ("ROLE_ADMIN".equals(role)) {
            Admin admin = adminRepository.findByLoginId(user.getEmail());
            if (admin == null) {
                response.sendRedirect(buildWebUrl("/login"));
                return;
            }
            clearAuthCookies(response);
            adminAuthService.startSession(admin, request.getSession(true));
            response.sendRedirect(buildWebUrl("/admin/verify"));
            return;
        }

        long accessExpiryMs = 600000L;
        long refreshExpiryMs = 86400000L;
        String access = jwtUtil.createJwt("access", username, role, accessExpiryMs);
        String refresh = jwtUtil.createJwt("refresh", username, role, refreshExpiryMs);

        refreshRepository.save(username, refresh, refreshExpiryMs);

        response.setHeader("access", access);
        response.addCookie(createCookie("access", access, Math.toIntExact(accessExpiryMs / 1000)));
        response.addCookie(createCookie("refresh", refresh, Math.toIntExact(refreshExpiryMs / 1000)));
        response.sendRedirect(resolveRedirectUrl(role));
    }

    private Cookie createCookie(String key, String value, int maxAge) {

        Cookie cookie = new Cookie(key, value);

        cookie.setMaxAge(maxAge);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setHttpOnly(true);

        return cookie;
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addCookie(expireCookie("access"));
        response.addCookie(expireCookie("refresh"));
    }

    private Cookie expireCookie(String key) {
        Cookie cookie = new Cookie(key, null);
        cookie.setMaxAge(0);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    private String resolveRedirectUrl(String role) {
        if (role != null && role.startsWith("ROLE_SELLER")) {
            return buildWebUrl("/seller");
        }
        return buildWebUrl("/");
    }

    private String buildWebUrl(String path) {
        String base = webBaseUrl == null ? "" : webBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

}
