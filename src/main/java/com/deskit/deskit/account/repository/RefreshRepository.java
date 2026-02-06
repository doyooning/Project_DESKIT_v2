package com.deskit.deskit.account.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RefreshRepository {
    private static final String PREFIX = "refresh:";

    private final RedisTemplate<String, String> redisTemplate;

    public RefreshRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String username, String refresh, long expiredMs) {
        redisTemplate.opsForValue().set(key(refresh), username, Duration.ofMillis(expiredMs));
    }

    public boolean existsByRefresh(String refresh) {
        Boolean exists = redisTemplate.hasKey(key(refresh));
        return Boolean.TRUE.equals(exists);
    }

    public void deleteByRefresh(String refresh) {
        redisTemplate.delete(key(refresh));
    }

    private String key(String refresh) {
        return PREFIX + refresh;
    }
}
