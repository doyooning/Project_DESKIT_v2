package com.deskit.deskit.account.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshRepositoryTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RefreshRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new RefreshRepository(redisTemplate);
    }

    @Test
    void saveStoresRefreshTokenWithPrefixAndTtl() {
        repository.save("user1", "refresh-token", 86400000L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("refresh:refresh-token");
        assertThat(valueCaptor.getValue()).isEqualTo("user1");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMillis(86400000L));
    }

    @Test
    void existsByRefreshReturnsTrueWhenRedisHasKey() {
        when(redisTemplate.hasKey("refresh:refresh-token")).thenReturn(true);

        assertThat(repository.existsByRefresh("refresh-token")).isTrue();
    }

    @Test
    void existsByRefreshReturnsFalseWhenRedisReturnsNull() {
        when(redisTemplate.hasKey("refresh:refresh-token")).thenReturn(null);

        assertThat(repository.existsByRefresh("refresh-token")).isFalse();
    }

    @Test
    void deleteByRefreshDeletesPrefixedKey() {
        repository.deleteByRefresh("refresh-token");

        verify(redisTemplate).delete("refresh:refresh-token");
    }

    @Test
    void rotateReturnsTrueWhenOldRefreshExists() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        boolean rotated = repository.rotate("old-refresh", "user1", "new-refresh", 86400000L);

        assertThat(rotated).isTrue();
    }

    @Test
    void rotateReturnsFalseWhenOldRefreshMissing() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        boolean rotated = repository.rotate("old-refresh", "user1", "new-refresh", 86400000L);

        assertThat(rotated).isFalse();
    }
}
