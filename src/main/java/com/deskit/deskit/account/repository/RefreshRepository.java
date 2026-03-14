package com.deskit.deskit.account.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
public class RefreshRepository {
    private static final String PREFIX = "refresh:";

    private static final String ROTATE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('DEL', KEYS[1])
                redis.call('PSETEX', KEYS[2], ARGV[2], ARGV[1])
                return 1
            end
            return 0
            """;

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

    public boolean rotate(String oldRefresh, String username, String newRefresh, long expiredMs) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ROTATE_SCRIPT, Long.class);
        Long result = redisTemplate.execute(
                script,
                List.of(key(oldRefresh), key(newRefresh)),
                username,
                String.valueOf(expiredMs)
        );
        return Long.valueOf(1L).equals(result);
    }

    private String key(String refresh) {
        return PREFIX + refresh;
    }
}
