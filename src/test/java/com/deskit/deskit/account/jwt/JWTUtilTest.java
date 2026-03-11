package com.deskit.deskit.account.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTUtilTest {

    private final JWTUtil jwtUtil = new JWTUtil("01234567890123456789012345678901");

    @Test
    void createJwtAndReadClaims() {
        String token = jwtUtil.createJwt("access", "user1", "ROLE_MEMBER", 60_000L);

        assertThat(jwtUtil.getCategory(token)).isEqualTo("access");
        assertThat(jwtUtil.getUsername(token)).isEqualTo("user1");
        assertThat(jwtUtil.getRole(token)).isEqualTo("ROLE_MEMBER");
        assertThat(jwtUtil.isExpired(token)).isFalse();
        assertThat(jwtUtil.getRemainingMs(token)).isPositive();
    }

    @Test
    void createSignupJwtContainsOptionalClaims() {
        String token = jwtUtil.createSignupJwt(
                "user2",
                "ROLE_GUEST",
                "name",
                "email@test.com",
                "profile-url",
                60_000L
        );

        assertThat(jwtUtil.getCategory(token)).isEqualTo("access");
        assertThat(jwtUtil.getUsername(token)).isEqualTo("user2");
        assertThat(jwtUtil.getRole(token)).isEqualTo("ROLE_GUEST");
        assertThat(jwtUtil.getName(token)).isEqualTo("name");
        assertThat(jwtUtil.getEmail(token)).isEqualTo("email@test.com");
        assertThat(jwtUtil.getProfileUrl(token)).isEqualTo("profile-url");
    }

    @Test
    void isExpiredReturnsTrueForExpiredToken() {
        String expiredToken = jwtUtil.createJwt("access", "user3", "ROLE_MEMBER", -1L);

        assertThatThrownBy(() -> jwtUtil.isExpired(expiredToken))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        assertThatThrownBy(() -> jwtUtil.getRemainingMs(expiredToken))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
