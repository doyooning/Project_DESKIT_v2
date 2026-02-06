package com.deskit.deskit.admin.controller;

import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.RefreshRepository;
import com.deskit.deskit.admin.dto.AdminAuthPendingResponse;
import com.deskit.deskit.admin.dto.AdminAuthVerifyRequest;
import com.deskit.deskit.admin.dto.AdminAuthVerifyResponse;
import com.deskit.deskit.admin.entity.Admin;
import com.deskit.deskit.admin.repository.AdminRepository;
import com.deskit.deskit.admin.service.AdminAuthService;
import com.deskit.deskit.common.util.verification.PhoneSendResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminRepository adminRepository;
    private final AdminAuthService adminAuthService;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    @GetMapping("/pending")
    public ResponseEntity<?> pending(HttpSession session) {
        String loginId = adminAuthService.getLoginId(session);
        if (loginId == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String name = adminAuthService.getName(session);
        String phone = adminAuthService.getPhone(session);

        AdminAuthPendingResponse response = AdminAuthPendingResponse.builder()
                .name(name == null ? "" : name)
                .email(loginId)
                .phoneMasked(maskPhone(phone))
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendCode(HttpSession session) {
        String loginId = adminAuthService.getLoginId(session);
        if (loginId == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String code = adminAuthService.resendCode(session);
        if (code == null) {
            Admin admin = adminRepository.findByLoginId(loginId);
            if (admin == null) {
                return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
            }
            code = adminAuthService.startSession(admin, session);
        }

        PhoneSendResponse response = PhoneSendResponse.builder()
                .message("verification code generated")
                .code(code)
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCode(
            @RequestBody AdminAuthVerifyRequest request,
            HttpSession session,
            HttpServletResponse response
    ) {
        String loginId = adminAuthService.getLoginId(session);
        if (loginId == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String code = request == null ? null : request.getCode();
        if (code == null || code.isBlank()) {
            return new ResponseEntity<>("verification code required", HttpStatus.BAD_REQUEST);
        }

        boolean verified = adminAuthService.verifyCode(session, code);
        if (!verified) {
            return new ResponseEntity<>("verification failed", HttpStatus.BAD_REQUEST);
        }

        String role = adminAuthService.getRole(session);
        String name = adminAuthService.getName(session);

        issueTokens(loginId, role == null ? "ROLE_ADMIN" : role, response);
        adminAuthService.clearSession(session);

        AdminAuthVerifyResponse payload = AdminAuthVerifyResponse.builder()
                .name(name == null ? "관리자" : name)
                .email(loginId)
                .role(role == null ? "ROLE_ADMIN" : role)
                .build();

        return new ResponseEntity<>(payload, HttpStatus.OK);
    }

    private void issueTokens(String username, String role, HttpServletResponse response) {
        long accessExpiryMs = 1800000L;
        long refreshExpiryMs = 86400000L;

        String access = jwtUtil.createJwt("access", username, role, accessExpiryMs);
        String refresh = jwtUtil.createJwt("refresh", username, role, refreshExpiryMs);

        refreshRepository.save(username, refresh, refreshExpiryMs);

        response.setHeader("access", access);
        response.addCookie(createCookie("access", access, Math.toIntExact(accessExpiryMs / 1000)));
        response.addCookie(createCookie("refresh", refresh, Math.toIntExact(refreshExpiryMs / 1000)));
    }

    private Cookie createCookie(String key, String value, int maxAge) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAge);
        // cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return phone;
        }
        String tail = digits.substring(digits.length() - 4);
        return "****" + tail;
    }
}
