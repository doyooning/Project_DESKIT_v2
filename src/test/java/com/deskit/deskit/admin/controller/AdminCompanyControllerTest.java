package com.deskit.deskit.admin.controller;

import com.deskit.deskit.admin.dto.AdminCompanyPageResponse;
import com.deskit.deskit.admin.dto.AdminCompanyResponse;
import com.deskit.deskit.admin.service.AdminCompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCompanyControllerTest {

    private final AdminCompanyService adminCompanyService = mock(AdminCompanyService.class);
    private final AdminCompanyController controller = new AdminCompanyController(adminCompanyService);

    @Test
    void listCompaniesReturnsForbiddenWhenAuthenticationMissing() {
        ResponseEntity<?> response = controller.listCompanies(null, 0, 10, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo("forbidden");
    }

    @Test
    void listCompaniesReturnsForbiddenWhenNotAdmin() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "n/a", "ROLE_MEMBER");
        auth.setAuthenticated(true);

        ResponseEntity<?> response = controller.listCompanies(auth, 0, 10, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listCompaniesReturnsForbiddenWhenAuthenticationNotAuthenticated() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "n/a");
        auth.setAuthenticated(false);

        ResponseEntity<?> response = controller.listCompanies(auth, 0, 10, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listCompaniesReturnsPageWhenAdmin() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN");
        auth.setAuthenticated(true);
        AdminCompanyPageResponse page = new AdminCompanyPageResponse(
                List.of(new AdminCompanyResponse("company-1", "상호", "대표", "123-45", "A", "2026-12-31", "활성", "2026-01-01")),
                0,
                10,
                1L,
                1
        );
        when(adminCompanyService.listCompanies(0, 10, "key", "상호", "123", "A", "활성", "2026-01-01", "2026-12-31"))
                .thenReturn(page);

        ResponseEntity<?> response = controller.listCompanies(auth, 0, 10, "key", "상호", "123", "A", "활성", "2026-01-01", "2026-12-31");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(page);
        verify(adminCompanyService).listCompanies(0, 10, "key", "상호", "123", "A", "활성", "2026-01-01", "2026-12-31");
    }
}
