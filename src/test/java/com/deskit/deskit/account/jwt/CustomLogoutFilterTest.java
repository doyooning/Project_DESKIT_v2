package com.deskit.deskit.account.jwt;

import com.deskit.deskit.account.repository.AccessBlacklistRepository;
import com.deskit.deskit.account.repository.RefreshRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomLogoutFilterTest {

    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final AccessBlacklistRepository accessBlacklistRepository = mock(AccessBlacklistRepository.class);
    private final CustomLogoutFilter filter =
            new CustomLogoutFilter(jwtUtil, refreshRepository, accessBlacklistRepository, false);

    @Test
    void doFilterPassesThroughWhenPathIsNotLogout() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
    }

    @Test
    void doFilterPassesThroughWhenMethodIsNotPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
    }

    @Test
    void doFilterClearsCookiesWhenRefreshMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        when(jwtUtil.isExpired("access-token")).thenReturn(false);
        when(jwtUtil.getCategory("access-token")).thenReturn("access");
        when(jwtUtil.getRemainingMs("access-token")).thenReturn(1000L);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(accessBlacklistRepository).blacklist("access-token", 1000L);
        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
        assertExpiredCookie(response, "access");
        assertExpiredCookie(response, "refresh");
    }

    @Test
    void doFilterDeletesRefreshWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.setCookies(new Cookie("refresh", "refresh-token"), new Cookie("access", "access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("access-token")).thenReturn(false);
        when(jwtUtil.getCategory("access-token")).thenReturn("access");
        when(jwtUtil.getRemainingMs("access-token")).thenReturn(1000L);
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getCategory("refresh-token")).thenReturn("refresh");
        when(refreshRepository.existsByRefresh("refresh-token")).thenReturn(true);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(refreshRepository).deleteByRefresh("refresh-token");
        verify(accessBlacklistRepository).blacklist("access-token", 1000L);
    }

    @Test
    void doFilterSkipsRefreshDeleteWhenRefreshExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.setCookies(new Cookie("refresh", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new ExpiredJwtException(null, null, "expired")).when(jwtUtil).isExpired("refresh-token");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
    }

    @Test
    void doFilterSkipsBlacklistWhenAccessInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.setCookies(new Cookie("refresh", "refresh-token"));
        request.addHeader("access", "legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("legacy-token")).thenReturn(false);
        when(jwtUtil.getCategory("legacy-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getCategory("refresh-token")).thenReturn("refresh");
        when(refreshRepository.existsByRefresh("refresh-token")).thenReturn(false);

        filter.doFilter(request, response, new MockFilterChain());

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
    }

    @Test
    void doFilterSkipsRefreshDeleteWhenRefreshCategoryInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.setCookies(new Cookie("refresh", "refresh-token"), new Cookie("access", "access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("access-token")).thenReturn(false);
        when(jwtUtil.getCategory("access-token")).thenReturn("access");
        when(jwtUtil.getRemainingMs("access-token")).thenReturn(1000L);
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getCategory("refresh-token")).thenReturn("access");

        filter.doFilter(request, response, new MockFilterChain());

        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
    }

    @Test
    void doFilterContinuesWhenAccessTokenParsingThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.addHeader("Authorization", "Bearer access-token");
        request.setCookies(new Cookie("refresh", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new RuntimeException("parse fail")).when(jwtUtil).isExpired("access-token");
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getCategory("refresh-token")).thenReturn("refresh");
        when(refreshRepository.existsByRefresh("refresh-token")).thenReturn(true);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository).deleteByRefresh("refresh-token");
    }

    @Test
    void doFilterSkipsBlacklistWhenAccessAlreadyExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.addHeader("Authorization", "Bearer access-token");
        request.setCookies(new Cookie("refresh", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("access-token")).thenReturn(true);
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getCategory("refresh-token")).thenReturn("refresh");
        when(refreshRepository.existsByRefresh("refresh-token")).thenReturn(false);

        filter.doFilter(request, response, new MockFilterChain());

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
    }

    @Test
    void doFilterHandlesNoHeadersAndNoCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilterSkipsBlacklistWhenAuthorizationIsNotBearerAndLegacyBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.addHeader("Authorization", "Basic abc");
        request.addHeader("access", " ");
        request.setCookies(new Cookie("refresh", "refresh-token"), new Cookie("access", ""));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getCategory("refresh-token")).thenReturn("refresh");
        when(refreshRepository.existsByRefresh("refresh-token")).thenReturn(false);

        filter.doFilter(request, response, new MockFilterChain());

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
    }

    private void assertExpiredCookie(MockHttpServletResponse response, String name) {
        Cookie cookie = response.getCookie(name);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
    }
}
