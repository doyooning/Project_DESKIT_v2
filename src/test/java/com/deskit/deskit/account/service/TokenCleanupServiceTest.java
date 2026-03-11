package com.deskit.deskit.account.service;

import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.AccessBlacklistRepository;
import com.deskit.deskit.account.repository.RefreshRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class TokenCleanupServiceTest {

    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final AccessBlacklistRepository accessBlacklistRepository = mock(AccessBlacklistRepository.class);

    private final TokenCleanupService service =
            new TokenCleanupService(jwtUtil, refreshRepository, accessBlacklistRepository);

    @Test
    void clearDeletesRefreshBlacklistsAccessAndExpiresCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        request.setCookies(new Cookie("refresh", "refresh-token"));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("access-token")).thenReturn(false);
        when(jwtUtil.getCategory("access-token")).thenReturn("access");
        when(jwtUtil.getRemainingMs("access-token")).thenReturn(1000L);

        service.clear(request, response);

        verify(accessBlacklistRepository).blacklist("access-token", 1000L);
        verify(refreshRepository).deleteByRefresh("refresh-token");
        verify(jwtUtil).isExpired("access-token");
        verify(jwtUtil).getCategory("access-token");
        verify(jwtUtil).getRemainingMs("access-token");
        verifyHeaderAndExpiredCookies(response);
    }

    @Test
    void clearSkipsBlacklistWhenAccessExpired() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("access-token")).thenReturn(true);

        service.clear(request, response);

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository, never()).deleteByRefresh("refresh-token");
        verifyHeaderAndExpiredCookies(response);
    }

    @Test
    void clearContinuesWhenAccessTokenParsingFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("access", "legacy-token");
        request.setCookies(new Cookie("refresh", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new RuntimeException("parse failed")).when(jwtUtil).isExpired("legacy-token");

        service.clear(request, response);

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository).deleteByRefresh("refresh-token");
        verifyHeaderAndExpiredCookies(response);
    }

    @Test
    void clearSkipsBlacklistWhenAccessCategoryIsNotAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("access", "legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.isExpired("legacy-token")).thenReturn(false);
        when(jwtUtil.getCategory("legacy-token")).thenReturn("refresh");

        service.clear(request, response);

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository, never()).deleteByRefresh(anyString());
        verifyHeaderAndExpiredCookies(response);
    }

    @Test
    void clearSkipsBlacklistWhenAccessMissingInAllLocations() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(request, response);

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository).deleteByRefresh("refresh-token");
        verifyHeaderAndExpiredCookies(response);
    }

    @Test
    void clearSkipsBlacklistWhenAuthorizationIsNotBearerAndNoFallbackToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(request, response);

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository, never()).deleteByRefresh(anyString());
        verifyHeaderAndExpiredCookies(response);
    }

    @Test
    void clearSkipsBlacklistWhenLegacyHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("access", " ");
        request.setCookies(new Cookie("refresh", "refresh-token"), new Cookie("access", ""));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(request, response);

        verify(accessBlacklistRepository, never()).blacklist(anyString(), anyLong());
        verify(refreshRepository).deleteByRefresh("refresh-token");
        verifyHeaderAndExpiredCookies(response);
    }

    private void verifyHeaderAndExpiredCookies(MockHttpServletResponse response) {
        org.assertj.core.api.Assertions.assertThat(response.getHeader("access")).isEqualTo("");
        Cookie[] cookies = response.getCookies();
        org.assertj.core.api.Assertions.assertThat(cookies).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(cookies[0].getMaxAge()).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(cookies[1].getMaxAge()).isEqualTo(0);
    }
}
