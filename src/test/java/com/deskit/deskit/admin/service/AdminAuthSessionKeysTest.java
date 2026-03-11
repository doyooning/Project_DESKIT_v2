package com.deskit.deskit.admin.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthSessionKeysTest {

    @Test
    void constantsAreDefined() {
        assertThat(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID).isEqualTo("pendingAdminLoginId");
        assertThat(AdminAuthSessionKeys.SESSION_ADMIN_PHONE).isEqualTo("pendingAdminPhone");
        assertThat(AdminAuthSessionKeys.SESSION_ADMIN_NAME).isEqualTo("pendingAdminName");
        assertThat(AdminAuthSessionKeys.SESSION_ADMIN_ROLE).isEqualTo("pendingAdminRole");
        assertThat(AdminAuthSessionKeys.SESSION_ADMIN_CODE).isEqualTo("pendingAdminCode");
        assertThat(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED).isEqualTo("pendingAdminVerified");
    }

    @Test
    void privateConstructorCanBeInvokedByReflection() throws Exception {
        Constructor<AdminAuthSessionKeys> constructor = AdminAuthSessionKeys.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isInstanceOf(AdminAuthSessionKeys.class);
    }
}
