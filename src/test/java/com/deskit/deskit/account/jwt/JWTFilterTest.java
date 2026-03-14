package com.deskit.deskit.account.jwt;

import com.deskit.deskit.account.repository.AccessBlacklistRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JWTFilterTest {

    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final AccessBlacklistRepository accessBlacklistRepository = mock(AccessBlacklistRepository.class);
    private final JWTFilter filter = new JWTFilter(jwtUtil, accessBlacklistRepository);
    private final ExposedJWTFilter exposedFilter = new ExposedJWTFilter(jwtUtil, accessBlacklistRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterPassesThroughWhenAccessTokenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(accessBlacklistRepository, never()).isBlacklisted("token");
    }

    @Test
    void doFilterReturnsUnauthorizedWhenTokenBlacklisted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accessBlacklistRepository.isBlacklisted("access-token")).thenReturn(true);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterReturnsUnauthorizedWhenTokenExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accessBlacklistRepository.isBlacklisted("access-token")).thenReturn(false);
        doThrow(new ExpiredJwtException(null, null, "expired")).when(jwtUtil).isExpired("access-token");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterReturnsUnauthorizedWhenCategoryNotAccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accessBlacklistRepository.isBlacklisted("access-token")).thenReturn(false);
        when(jwtUtil.isExpired("access-token")).thenReturn(false);
        when(jwtUtil.getCategory("access-token")).thenReturn("refresh");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterReturnsUnauthorizedWhenTokenMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer malformed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accessBlacklistRepository.isBlacklisted("malformed-token")).thenReturn(false);
        doThrow(new MalformedJwtException("malformed")).when(jwtUtil).isExpired("malformed-token");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilterSetsAuthenticationWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setCookies(new jakarta.servlet.http.Cookie("access", "cookie-access"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(accessBlacklistRepository.isBlacklisted("cookie-access")).thenReturn(false);
        when(jwtUtil.isExpired("cookie-access")).thenReturn(false);
        when(jwtUtil.getCategory("cookie-access")).thenReturn("access");
        when(jwtUtil.getUsername("cookie-access")).thenReturn("user1");
        when(jwtUtil.getRole("cookie-access")).thenReturn("ROLE_MEMBER");
        when(jwtUtil.getName("cookie-access")).thenReturn("name");
        when(jwtUtil.getEmail("cookie-access")).thenReturn("email@test.com");
        when(jwtUtil.getProfileUrl("cookie-access")).thenReturn("profile");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("name");
        verify(jwtUtil).getUsername("cookie-access");
        verify(jwtUtil).getRole("cookie-access");
    }

    @Test
    void doFilterUsesLegacyHeaderWhenAuthorizationMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("access", "legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accessBlacklistRepository.isBlacklisted("legacy-token")).thenReturn(false);
        when(jwtUtil.isExpired("legacy-token")).thenReturn(false);
        when(jwtUtil.getCategory("legacy-token")).thenReturn("access");
        when(jwtUtil.getUsername("legacy-token")).thenReturn("user1");
        when(jwtUtil.getRole("legacy-token")).thenReturn("ROLE_MEMBER");
        when(jwtUtil.getName("legacy-token")).thenReturn("name");
        when(jwtUtil.getEmail("legacy-token")).thenReturn("email@test.com");
        when(jwtUtil.getProfileUrl("legacy-token")).thenReturn("profile");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(jwtUtil).isExpired("legacy-token");
    }

    @Test
    void doFilterSkipsForOauthAndWsAndTestAuthPaths() throws Exception {
        assertSkippedPath("/oauth2/authorization/google");
        assertSkippedPath("/login/oauth2/code/google");
        assertSkippedPath("/login");
        assertSkippedPath("/ws/channel");
        assertSkippedPath("/ws");
        assertSkippedPath("/api/ws/connect");
        assertSkippedPath("/api/internal/test-auth/seller-token");
        assertSkippedPath("/internal/test-auth/seller-token");
        assertSkippedPath("/x/api/internal/test-auth/seller-token");
        assertSkippedPath("/x/internal/test-auth/seller-token");
    }

    @Test
    void doFilterPassesThroughWhenCookieDoesNotContainAccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Basic abc");
        request.addHeader("access", " ");
        request.setCookies(new jakarta.servlet.http.Cookie("other", "value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilterPassesThroughWhenAccessCookieIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setCookies(new jakarta.servlet.http.Cookie("access", ""));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldNotFilterCoversAllMatcherBranches() {
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/oauth2/a"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/login/oauth2/a"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/login"))).isTrue();

        assertThat(exposedFilter.callShouldNotFilter(requestFor("/ws/a"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/ws"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/api/ws/a"))).isTrue();

        assertThat(exposedFilter.callShouldNotFilter(requestFor("/api/internal/test-auth/a"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/internal/test-auth/a"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/x/api/internal/test-auth/a"))).isTrue();
        assertThat(exposedFilter.callShouldNotFilter(requestFor("/x/internal/test-auth/a"))).isTrue();

        assertThat(exposedFilter.callShouldNotFilter(requestFor("/api/protected"))).isFalse();
    }

    private void assertSkippedPath(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest requestFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setRequestURI(path);
        return request;
    }

    private static class ExposedJWTFilter extends JWTFilter {
        ExposedJWTFilter(JWTUtil jwtUtil, AccessBlacklistRepository accessBlacklistRepository) {
            super(jwtUtil, accessBlacklistRepository);
        }

        boolean callShouldNotFilter(HttpServletRequest request) {
            return super.shouldNotFilter(request);
        }
    }
}
