package com.deskit.deskit.account.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class AccessBlacklistRepository {

    private static final String PREFIX = "blacklist:access:";

    private final RedisTemplate<String, String> redisTemplate;

    public AccessBlacklistRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String accessToken, long ttlMs) {
        if (accessToken == null || accessToken.isBlank() || ttlMs <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(key(accessToken), "1", Duration.ofMillis(ttlMs));
    }

    public boolean isBlacklisted(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(key(accessToken));
        return Boolean.TRUE.equals(exists);
    }

    private String key(String accessToken) {
        return PREFIX + accessToken;
    }
}
