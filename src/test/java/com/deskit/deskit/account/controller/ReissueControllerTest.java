package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.RefreshRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReissueController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReissueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JWTUtil jwtUtil;

    @MockBean
    private RefreshRepository refreshRepository;

    @Test
    void reissueReturnsBadRequestWhenRefreshMissing() throws Exception {
        mockMvc.perform(post("/api/reissue")
                        .cookie(new Cookie("other", "value")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("refresh token null"));
    }

    @Test
    void reissueReturnsBadRequestWhenNoCookies() throws Exception {
        mockMvc.perform(post("/api/reissue"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("refresh token null"));
    }

    @Test
    void reissueReturnsBadRequestWhenRefreshExpired() throws Exception {
        String refreshToken = "refresh-token";

        doThrow(new ExpiredJwtException(null, null, "expired"))
                .when(jwtUtil).isExpired(refreshToken);

        mockMvc.perform(post("/api/reissue")
                        .cookie(new Cookie("refresh", refreshToken)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("refresh token expired"));
    }

    @Test
    void reissueReturnsBadRequestWhenRefreshCategoryInvalid() throws Exception {
        String refreshToken = "refresh-token";

        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(jwtUtil.getCategory(refreshToken)).thenReturn("access");

        mockMvc.perform(post("/api/reissue")
                        .cookie(new Cookie("refresh", refreshToken)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("invalid refresh token"));
    }

    @Test
    void reissueReturnsBadRequestWhenRefreshNotInStore() throws Exception {
        String refreshToken = "refresh-token";

        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(jwtUtil.getCategory(refreshToken)).thenReturn("refresh");
        when(refreshRepository.existsByRefresh(refreshToken)).thenReturn(false);

        mockMvc.perform(post("/api/reissue")
                        .cookie(new Cookie("refresh", refreshToken)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("invalid refresh token"));
    }

    @Test
    void reissueReturnsNewTokensAndRotatesRefresh() throws Exception {
        String refreshToken = "refresh-token";
        String username = "user1";
        String role = "ROLE_MEMBER";
        String newAccess = "new-access";
        String newRefresh = "new-refresh";

        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(jwtUtil.getCategory(refreshToken)).thenReturn("refresh");
        when(refreshRepository.existsByRefresh(refreshToken)).thenReturn(true);
        when(jwtUtil.getUsername(refreshToken)).thenReturn(username);
        when(jwtUtil.getRole(refreshToken)).thenReturn(role);
        when(jwtUtil.createJwt("access", username, role, 600000L)).thenReturn(newAccess);
        when(jwtUtil.createJwt("refresh", username, role, 86400000L)).thenReturn(newRefresh);

        mockMvc.perform(post("/api/reissue")
                        .cookie(new Cookie("refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("access", newAccess))
                .andExpect(cookie().value("access", newAccess))
                .andExpect(cookie().value("refresh", newRefresh))
                .andExpect(cookie().httpOnly("access", true))
                .andExpect(cookie().httpOnly("refresh", true));

        verify(refreshRepository).deleteByRefresh(refreshToken);

        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refreshCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(refreshRepository).save(usernameCaptor.capture(), refreshCaptor.capture(), ttlCaptor.capture());

        assertThat(usernameCaptor.getValue()).isEqualTo(username);
        assertThat(refreshCaptor.getValue()).isEqualTo(newRefresh);
        assertThat(ttlCaptor.getValue()).isEqualTo(86400000L);
    }
}
