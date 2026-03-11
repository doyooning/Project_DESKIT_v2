package com.deskit.deskit.account.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessBlacklistRepositoryTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AccessBlacklistRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new AccessBlacklistRepository(redisTemplate);
    }

    @Test
    void blacklistStoresTokenWithTtl() {
        repository.blacklist("access-token", 5000L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("blacklist:access:access-token");
        assertThat(valueCaptor.getValue()).isEqualTo("1");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMillis(5000L));
    }

    @Test
    void blacklistSkipsWhenTokenBlankOrTtlNotPositive() {
        repository.blacklist(null, 5000L);
        repository.blacklist(" ", 5000L);
        repository.blacklist("access-token", 0L);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void isBlacklistedReturnsFalseWhenTokenBlank() {
        assertThat(repository.isBlacklisted(null)).isFalse();
        assertThat(repository.isBlacklisted(" ")).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void isBlacklistedReturnsTrueWhenRedisHasKey() {
        when(redisTemplate.hasKey("blacklist:access:access-token")).thenReturn(true);

        boolean actual = repository.isBlacklisted("access-token");

        assertThat(actual).isTrue();
    }

    @Test
    void isBlacklistedReturnsFalseWhenRedisReturnsNull() {
        when(redisTemplate.hasKey("blacklist:access:access-token")).thenReturn(null);

        boolean actual = repository.isBlacklisted("access-token");

        assertThat(actual).isFalse();
    }
}
