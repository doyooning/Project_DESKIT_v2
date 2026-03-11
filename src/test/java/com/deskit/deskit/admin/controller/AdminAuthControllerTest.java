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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthControllerTest {

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AdminAuthService adminAuthService = mock(AdminAuthService.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);

    private final AdminAuthController controller =
            new AdminAuthController(adminRepository, adminAuthService, jwtUtil, refreshRepository);

    @Test
    void pendingReturnsUnauthorizedWhenNoSessionLoginId() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn(null);

        ResponseEntity<?> response = controller.pending(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("unauthorized");
    }

    @Test
    void pendingReturnsMaskedPhone() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.getName(session)).thenReturn("관리자");
        when(adminAuthService.getPhone(session)).thenReturn("010-1234-5678");

        ResponseEntity<?> response = controller.pending(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AdminAuthPendingResponse body = (AdminAuthPendingResponse) response.getBody();
        assertThat(body.getName()).isEqualTo("관리자");
        assertThat(body.getEmail()).isEqualTo("admin@deskit.com");
        assertThat(body.getPhoneMasked()).isEqualTo("****5678");
    }

    @Test
    void sendCodeReturnsUnauthorizedWhenNoLoginId() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn(null);

        ResponseEntity<?> response = controller.sendCode(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("unauthorized");
    }

    @Test
    void sendCodeUsesResendWhenSessionAlreadyExists() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.resendCode(session)).thenReturn("123456");

        ResponseEntity<?> response = controller.sendCode(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PhoneSendResponse body = (PhoneSendResponse) response.getBody();
        assertThat(body.getCode()).isEqualTo("123456");
        verify(adminRepository, never()).findByLoginId("admin@deskit.com");
    }

    @Test
    void sendCodeReturnsUnauthorizedWhenAdminNotFound() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.resendCode(session)).thenReturn(null);
        when(adminRepository.findByLoginId("admin@deskit.com")).thenReturn(null);

        ResponseEntity<?> response = controller.sendCode(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("unauthorized");
    }

    @Test
    void sendCodeStartsSessionWhenNeeded() {
        MockHttpSession session = new MockHttpSession();
        Admin admin = Admin.builder()
                .loginId("admin@deskit.com")
                .name("관리자")
                .phone("01011112222")
                .role("ROLE_ADMIN")
                .build();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.resendCode(session)).thenReturn(null);
        when(adminRepository.findByLoginId("admin@deskit.com")).thenReturn(admin);
        when(adminAuthService.startSession(admin, session)).thenReturn("654321");

        ResponseEntity<?> response = controller.sendCode(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PhoneSendResponse body = (PhoneSendResponse) response.getBody();
        assertThat(body.getCode()).isEqualTo("654321");
    }

    @Test
    void verifyCodeReturnsUnauthorizedWhenNoLoginId() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn(null);

        ResponseEntity<?> response = controller.verifyCode(new AdminAuthVerifyRequest(), session, new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("unauthorized");
    }

    @Test
    void verifyCodeReturnsBadRequestWhenCodeBlank() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        AdminAuthVerifyRequest request = new AdminAuthVerifyRequest();
        request.setCode(" ");

        ResponseEntity<?> response = controller.verifyCode(request, session, new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("verification code required");
    }

    @Test
    void verifyCodeReturnsBadRequestWhenRequestBodyMissing() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");

        ResponseEntity<?> response = controller.verifyCode(null, session, new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("verification code required");
    }

    @Test
    void verifyCodeReturnsBadRequestWhenVerificationFails() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.verifyCode(session, "111111")).thenReturn(false);
        AdminAuthVerifyRequest request = new AdminAuthVerifyRequest();
        request.setCode("111111");

        ResponseEntity<?> response = controller.verifyCode(request, session, new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("verification failed");
    }

    @Test
    void verifyCodeIssuesTokensAndClearsSession() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        AdminAuthVerifyRequest request = new AdminAuthVerifyRequest();
        request.setCode("123456");

        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.verifyCode(session, "123456")).thenReturn(true);
        when(adminAuthService.getRole(session)).thenReturn(null);
        when(adminAuthService.getName(session)).thenReturn(null);
        when(jwtUtil.createJwt("access", "admin@deskit.com", "ROLE_ADMIN", 1800000L)).thenReturn("access-token");
        when(jwtUtil.createJwt("refresh", "admin@deskit.com", "ROLE_ADMIN", 86400000L)).thenReturn("refresh-token");

        ResponseEntity<?> response = controller.verifyCode(request, session, servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AdminAuthVerifyResponse body = (AdminAuthVerifyResponse) response.getBody();
        assertThat(body.getName()).isEqualTo("관리자");
        assertThat(body.getEmail()).isEqualTo("admin@deskit.com");
        assertThat(body.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(servletResponse.getHeader("access")).isEqualTo("access-token");
        assertThat(servletResponse.getCookie("access")).isNotNull();
        assertThat(servletResponse.getCookie("refresh")).isNotNull();
        verify(refreshRepository).save("admin@deskit.com", "refresh-token", 86400000L);
        verify(adminAuthService).clearSession(session);
    }

    @Test
    void verifyCodeUsesSessionRoleAndNameWhenPresent() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        AdminAuthVerifyRequest request = new AdminAuthVerifyRequest();
        request.setCode("123456");

        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.verifyCode(session, "123456")).thenReturn(true);
        when(adminAuthService.getRole(session)).thenReturn("ROLE_SUPER_ADMIN");
        when(adminAuthService.getName(session)).thenReturn("실관리자");
        when(jwtUtil.createJwt("access", "admin@deskit.com", "ROLE_SUPER_ADMIN", 1800000L)).thenReturn("access-token");
        when(jwtUtil.createJwt("refresh", "admin@deskit.com", "ROLE_SUPER_ADMIN", 86400000L)).thenReturn("refresh-token");

        ResponseEntity<?> response = controller.verifyCode(request, session, servletResponse);

        AdminAuthVerifyResponse body = (AdminAuthVerifyResponse) response.getBody();
        assertThat(body.getName()).isEqualTo("실관리자");
        assertThat(body.getRole()).isEqualTo("ROLE_SUPER_ADMIN");
        verify(refreshRepository).save("admin@deskit.com", "refresh-token", 86400000L);
    }

    @Test
    void pendingHandlesNullAndShortPhoneValues() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.getName(session)).thenReturn(null);
        when(adminAuthService.getPhone(session)).thenReturn("1234");

        ResponseEntity<?> response = controller.pending(session);
        AdminAuthPendingResponse body = (AdminAuthPendingResponse) response.getBody();
        assertThat(body.getName()).isEqualTo("");
        assertThat(body.getPhoneMasked()).isEqualTo("1234");
    }

    @Test
    void pendingMasksBlankPhoneAsEmptyString() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.getName(session)).thenReturn("관리자");
        when(adminAuthService.getPhone(session)).thenReturn(" ");

        ResponseEntity<?> response = controller.pending(session);

        AdminAuthPendingResponse body = (AdminAuthPendingResponse) response.getBody();
        assertThat(body.getPhoneMasked()).isEqualTo("");
    }

    @Test
    void pendingMasksNullPhoneAsEmptyString() {
        MockHttpSession session = new MockHttpSession();
        when(adminAuthService.getLoginId(session)).thenReturn("admin@deskit.com");
        when(adminAuthService.getName(session)).thenReturn("관리자");
        when(adminAuthService.getPhone(session)).thenReturn(null);

        ResponseEntity<?> response = controller.pending(session);

        AdminAuthPendingResponse body = (AdminAuthPendingResponse) response.getBody();
        assertThat(body.getPhoneMasked()).isEqualTo("");
    }
}
