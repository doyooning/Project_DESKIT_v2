package com.deskit.deskit.admin.service;

import com.deskit.deskit.admin.entity.Admin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthServiceTest {

    private final AdminAuthService service = new AdminAuthService();

    @Test
    void startSessionReturnsNullWhenInputsMissing() {
        assertThat(service.startSession(null, new MockHttpSession())).isNull();
        assertThat(service.startSession(Admin.builder().build(), null)).isNull();
    }

    @Test
    void startSessionStoresAttributesAndReturnsCode() {
        Admin admin = Admin.builder()
                .loginId("admin@deskit.com")
                .phone("010-1234-5678")
                .name("관리자")
                .role("ROLE_ADMIN")
                .build();
        MockHttpSession session = new MockHttpSession();

        String code = service.startSession(admin, session);

        assertThat(code).matches("\\d{6}");
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID)).isEqualTo("admin@deskit.com");
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE)).isEqualTo("010-1234-5678");
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME)).isEqualTo("관리자");
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE)).isEqualTo("ROLE_ADMIN");
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE)).isEqualTo(code);
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED)).isEqualTo(false);
    }

    @Test
    void resendCodeReturnsNullWhenSessionMissingOrLoginIdBlank() {
        assertThat(service.resendCode(null)).isNull();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID, null);
        assertThat(service.resendCode(session)).isNull();

        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID, " ");
        assertThat(service.resendCode(session)).isNull();
    }

    @Test
    void resendCodeUpdatesCodeAndVerifiedFlag() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID, "admin@deskit.com");

        String code = service.resendCode(session);

        assertThat(code).matches("\\d{6}");
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE)).isEqualTo(code);
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED)).isEqualTo(false);
    }

    @Test
    void verifyCodeReturnsFalseForInvalidInputs() {
        MockHttpSession session = new MockHttpSession();
        assertThat(service.verifyCode(null, "123456")).isFalse();
        assertThat(service.verifyCode(session, null)).isFalse();

        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, null);
        assertThat(service.verifyCode(session, "123456")).isFalse();

        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, " ");
        assertThat(service.verifyCode(session, "123456")).isFalse();

        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, "123456");
        assertThat(service.verifyCode(session, "999999")).isFalse();
    }

    @Test
    void verifyCodeSetsVerifiedWhenMatched() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, "123456");

        boolean verified = service.verifyCode(session, "123456");

        assertThat(verified).isTrue();
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED)).isEqualTo(true);
    }

    @Test
    void clearSessionRemovesAllAttributesAndHandlesNull() {
        service.clearSession(null);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID, "a");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE, "b");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME, "c");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE, "d");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, "e");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED, true);

        service.clearSession(session);

        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID)).isNull();
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE)).isNull();
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME)).isNull();
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE)).isNull();
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE)).isNull();
        assertThat(session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED)).isNull();
    }

    @Test
    void getterMethodsReturnNullWhenSessionNullAndValueWhenPresent() {
        assertThat(service.getLoginId(null)).isNull();
        assertThat(service.getPhone(null)).isNull();
        assertThat(service.getName(null)).isNull();
        assertThat(service.getRole(null)).isNull();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID, "admin@deskit.com");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE, "010");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME, "name");
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE, "ROLE_ADMIN");

        assertThat(service.getLoginId(session)).isEqualTo("admin@deskit.com");
        assertThat(service.getPhone(session)).isEqualTo("010");
        assertThat(service.getName(session)).isEqualTo("name");
        assertThat(service.getRole(session)).isEqualTo("ROLE_ADMIN");
    }
}
