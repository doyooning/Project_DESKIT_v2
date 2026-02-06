package com.deskit.deskit.admin.service;

import com.deskit.deskit.admin.entity.Admin;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class AdminAuthService {

    public String startSession(Admin admin, HttpSession session) {
        if (admin == null || session == null) {
            return null;
        }

        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID, admin.getLoginId());
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE, admin.getPhone());
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME, admin.getName());
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE, admin.getRole());

        String code = generateCode();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, code);
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED, false);

        return code;
    }

    public String resendCode(HttpSession session) {
        if (session == null) {
            return null;
        }

        String loginId = (String) session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID);
        if (loginId == null || loginId.isBlank()) {
            return null;
        }

        String code = generateCode();
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE, code);
        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED, false);

        return code;
    }

    public boolean verifyCode(HttpSession session, String code) {
        if (session == null || code == null) {
            return false;
        }

        String storedCode = (String) session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE);
        if (storedCode == null || storedCode.isBlank()) {
            return false;
        }

        if (!storedCode.equals(code)) {
            return false;
        }

        session.setAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED, true);
        return true;
    }

    public void clearSession(HttpSession session) {
        if (session == null) {
            return;
        }

        session.removeAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID);
        session.removeAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE);
        session.removeAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME);
        session.removeAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE);
        session.removeAttribute(AdminAuthSessionKeys.SESSION_ADMIN_CODE);
        session.removeAttribute(AdminAuthSessionKeys.SESSION_ADMIN_VERIFIED);
    }

    public String getLoginId(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_LOGIN_ID);
    }

    public String getPhone(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_PHONE);
    }

    public String getName(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_NAME);
    }

    public String getRole(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(AdminAuthSessionKeys.SESSION_ADMIN_ROLE);
    }

    private String generateCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
    }
}
