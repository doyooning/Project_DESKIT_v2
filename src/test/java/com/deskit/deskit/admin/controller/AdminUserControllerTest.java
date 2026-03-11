package com.deskit.deskit.admin.controller;

import com.deskit.deskit.admin.dto.AdminUserPageResponse;
import com.deskit.deskit.admin.dto.AdminUserResponse;
import com.deskit.deskit.admin.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserControllerTest {

    private final AdminUserService adminUserService = mock(AdminUserService.class);
    private final AdminUserController controller = new AdminUserController(adminUserService);

    @Test
    void listUsersReturnsForbiddenWhenAuthenticationMissing() {
        ResponseEntity<?> response = controller.listUsers(null, 0, 10, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo("forbidden");
    }

    @Test
    void listUsersReturnsForbiddenWhenNotAdmin() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "n/a", "ROLE_MEMBER");
        auth.setAuthenticated(true);

        ResponseEntity<?> response = controller.listUsers(auth, 0, 10, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listUsersReturnsForbiddenWhenAuthenticationNotAuthenticated() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "n/a");
        auth.setAuthenticated(false);

        ResponseEntity<?> response = controller.listUsers(auth, 0, 10, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listUsersReturnsPageWhenAdmin() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        AdminUserPageResponse page = new AdminUserPageResponse(
                List.of(new AdminUserResponse("member-1", "a@a.com", "name", "일반회원", "활성", "010", "2026-01-01", "Email", true)),
                1,
                5,
                10L,
                2
        );
        when(adminUserService.listUsers(1, 5, "key", "일반회원", "활성", "2026-01-01", "2026-12-31"))
                .thenReturn(page);

        ResponseEntity<?> response = controller.listUsers(auth, 1, 5, "key", "일반회원", "활성", "2026-01-01", "2026-12-31");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(page);
        verify(adminUserService).listUsers(1, 5, "key", "일반회원", "활성", "2026-01-01", "2026-12-31");
    }
}
