package com.deskit.deskit.livehost.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public String getRealtimeViewKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":active_uv";
    }

    public String getSessionCountKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":session_counts";
    }

    public String getTotalUvKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":total_uv";
    }

    public String getLikeUsersKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":like_users";
    }

    public String getSanctionKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":sanctions";
    }

    public String getReportUsersKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":report_users";
    }

    public String getReportCountKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":reports";
    }

    public String getViewHistoryBufferKey(String type) {
        return "view_history:buffer:" + type;
    }

    public String getMaxViewersKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":max_viewers";
    }

    public String getMaxViewersTimeKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":max_viewers_time";
    }

    public String getVodStatsDirtyKey() {
        return "vod:stats:dirty";
    }

    public String getVodViewersKey(Long broadcastId) {
        return "vod:" + broadcastId + ":viewers";
    }

    public String getVodViewDeltaKey(Long broadcastId) {
        return "vod:" + broadcastId + ":view_delta";
    }

    public String getVodLikeUsersKey(Long broadcastId) {
        return getLikeUsersKey(broadcastId);
    }

    public String getVodLikeDeltaKey(Long broadcastId) {
        return "vod:" + broadcastId + ":like_delta";
    }

    public String getVodReportUsersKey(Long broadcastId) {
        return getReportUsersKey(broadcastId);
    }

    public String getVodReportDeltaKey(Long broadcastId) {
        return "vod:" + broadcastId + ":report_delta";
    }

    public String getMediaConfigKey(Long broadcastId, Long sellerId) {
        return "broadcast:" + broadcastId + ":media:" + sellerId;
    }

    public String getScheduleNoticeKey(Long broadcastId, String type) {
        return "broadcast:" + broadcastId + ":notice:" + type;
    }

    public String getOriginalPriceKey(Long broadcastId) {
        return "broadcast:" + broadcastId + ":original_price";
    }

    public String getRecordingRetryQueueKey() {
        return "broadcast:recording:retry";
    }

    public String getRecordingRetryAttemptKey(Long broadcastId) {
        return "broadcast:recording:retry:" + broadcastId + ":attempts";
    }

    public String getRecordingStartRetryQueueKey() {
        return "broadcast:recording:start:retry";
    }

    public String getRecordingStartRetryAttemptKey(Long broadcastId) {
        return "broadcast:recording:start:retry:" + broadcastId + ":attempts";
    }

    public Boolean acquireLock(String key, long timeoutMillis) {
        return redisTemplate.opsForValue()
                .setIfAbsent(key, "LOCKED", Duration.ofMillis(timeoutMillis));
    }

    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(result);
    }

    public void scheduleRecordingRetry(Long broadcastId, Duration delay) {
        long score = Instant.now().plus(delay).toEpochMilli();
        redisTemplate.opsForZSet().add(getRecordingRetryQueueKey(), broadcastId, score);
    }

    public void scheduleRecordingStartRetry(Long broadcastId, Duration delay) {
        long score = Instant.now().plus(delay).toEpochMilli();
        redisTemplate.opsForZSet().add(getRecordingStartRetryQueueKey(), broadcastId, score);
    }

    public Set<Long> popDueRecordingRetries(int count) {
        long now = Instant.now().toEpochMilli();
        Set<Object> due = redisTemplate.opsForZSet().rangeByScore(getRecordingRetryQueueKey(), 0, now, 0, count);
        if (due == null || due.isEmpty()) {
            return Set.of();
        }
        redisTemplate.opsForZSet().remove(getRecordingRetryQueueKey(), due.toArray());
        return due.stream()
                .map(value -> Long.parseLong(value.toString()))
                .collect(Collectors.toSet());
    }

    public Set<Long> popDueRecordingStartRetries(int count) {
        long now = Instant.now().toEpochMilli();
        Set<Object> due = redisTemplate.opsForZSet().rangeByScore(getRecordingStartRetryQueueKey(), 0, now, 0, count);
        if (due == null || due.isEmpty()) {
            return Set.of();
        }
        redisTemplate.opsForZSet().remove(getRecordingStartRetryQueueKey(), due.toArray());
        return due.stream()
                .map(value -> Long.parseLong(value.toString()))
                .collect(Collectors.toSet());
    }

    public int incrementRecordingRetryAttempt(Long broadcastId, Duration ttl) {
        String key = getRecordingRetryAttemptKey(broadcastId);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            redisTemplate.expire(key, ttl);
        }
        return value != null ? value.intValue() : 0;
    }

    public int incrementRecordingStartRetryAttempt(Long broadcastId, Duration ttl) {
        String key = getRecordingStartRetryAttemptKey(broadcastId);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            redisTemplate.expire(key, ttl);
        }
        return value != null ? value.intValue() : 0;
    }

    public void clearRecordingRetry(Long broadcastId) {
        redisTemplate.opsForZSet().remove(getRecordingRetryQueueKey(), broadcastId);
        redisTemplate.delete(getRecordingRetryAttemptKey(broadcastId));
    }

    public void clearRecordingStartRetry(Long broadcastId) {
        redisTemplate.opsForZSet().remove(getRecordingStartRetryQueueKey(), broadcastId);
        redisTemplate.delete(getRecordingStartRetryAttemptKey(broadcastId));
    }

    public void storeOriginalPrice(Long broadcastId, Long productId, Integer price) {
        if (price == null) {
            return;
        }
        redisTemplate.opsForHash().putIfAbsent(
                getOriginalPriceKey(broadcastId),
                productId.toString(),
                price
        );
    }

    public Integer getOriginalPrice(Long broadcastId, Long productId) {
        Object value = redisTemplate.opsForHash()
                .get(getOriginalPriceKey(broadcastId), productId.toString());
        return value != null ? Integer.valueOf(value.toString()) : null;
    }

    public void removeOriginalPrice(Long broadcastId, Long productId) {
        redisTemplate.opsForHash().delete(getOriginalPriceKey(broadcastId), productId.toString());
    }

    public void clearOriginalPrices(Long broadcastId) {
        redisTemplate.delete(getOriginalPriceKey(broadcastId));
    }

    public void enterLiveRoom(Long broadcastId, String uuid) {
        String sessionKey = getSessionCountKey(broadcastId);
        String activeKey = getRealtimeViewKey(broadcastId);
        String totalKey = getTotalUvKey(broadcastId);

        Long count = redisTemplate.opsForHash().increment(sessionKey, uuid, 1);

        if (count != null && count == 1) {
            redisTemplate.opsForSet().add(activeKey, uuid);
        }

        redisTemplate.opsForSet().add(totalKey, uuid);

        expireKey(sessionKey);
        expireKey(activeKey);
        expireKey(totalKey);
    }

    public void exitLiveRoom(Long broadcastId, String uuid) {
        String sessionKey = getSessionCountKey(broadcastId);
        String activeKey = getRealtimeViewKey(broadcastId);

        Long count = redisTemplate.opsForHash().increment(sessionKey, uuid, -1);

        if (count != null && count <= 0) {
            redisTemplate.opsForHash().delete(sessionKey, uuid);
            redisTemplate.opsForSet().remove(activeKey, uuid);
        }
    }

    public int getRealtimeViewerCount(Long broadcastId) {
        Long size = redisTemplate.opsForSet().size(getRealtimeViewKey(broadcastId));
        return size != null ? size.intValue() : 0;
    }

    public int getTotalUniqueViewerCount(Long broadcastId) {
        Long size = redisTemplate.opsForSet().size(getTotalUvKey(broadcastId));
        return size != null ? size.intValue() : 0;
    }

    public void bufferViewHistory(String type, Long broadcastId, String viewerId) {
        String value = broadcastId + ":" + viewerId + ":" + System.currentTimeMillis();
        redisTemplate.opsForList().rightPush(getViewHistoryBufferKey(type), value);
    }

    public List<String> popViewHistoryBuffer(String type, int count) {
        String key = getViewHistoryBufferKey(type);
        List<Object> objects = redisTemplate.opsForList().range(key, 0, count - 1);
        if (objects == null || objects.isEmpty()) {
            return List.of();
        }

        redisTemplate.opsForList().trim(key, count, -1);

        return objects.stream().map(Object::toString).collect(Collectors.toList());
    }

    public boolean toggleLike(Long broadcastId, Long memberId) {
        String key = getLikeUsersKey(broadcastId);

        Boolean isMember = redisTemplate.opsForSet().isMember(key, String.valueOf(memberId));

        if (Boolean.TRUE.equals(isMember)) {
            redisTemplate.opsForSet().remove(key, String.valueOf(memberId));
            return false;
        }

        redisTemplate.opsForSet().add(key, String.valueOf(memberId));
        return true;
    }

    public boolean isMemberLiked(Long broadcastId, Long memberId) {
        String key = getLikeUsersKey(broadcastId);
        Boolean isMember = redisTemplate.opsForSet().isMember(key, String.valueOf(memberId));
        return Boolean.TRUE.equals(isMember);
    }

    public int getLikeCount(Long broadcastId) {
        Long size = redisTemplate.opsForSet().size(getLikeUsersKey(broadcastId));
        return size != null ? size.intValue() : 0;
    }

    public boolean reportBroadcast(Long broadcastId, Long memberId) {
        String userKey = getReportUsersKey(broadcastId);
        String countKey = getReportCountKey(broadcastId);

        Long added = redisTemplate.opsForSet().add(userKey, String.valueOf(memberId));

        if (added != null && added == 1) {
            redisTemplate.opsForValue().increment(countKey);
            return true;
        }

        return false;
    }

    public int getReportCount(Long broadcastId) {
        return getInt(getReportCountKey(broadcastId));
    }

    public boolean recordVodView(Long broadcastId, String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            return false;
        }
        Long added = redisTemplate.opsForSet().add(getVodViewersKey(broadcastId), viewerId);
        if (added != null && added == 1) {
            redisTemplate.opsForValue().increment(getVodViewDeltaKey(broadcastId));
            markVodDirty(broadcastId);
            return true;
        }
        return false;
    }

    public boolean toggleVodLike(Long broadcastId, Long memberId) {
        String key = getLikeUsersKey(broadcastId);
        String memberKey = String.valueOf(memberId);

        Boolean isMember = redisTemplate.opsForSet().isMember(key, memberKey);
        if (Boolean.TRUE.equals(isMember)) {
            redisTemplate.opsForSet().remove(key, memberKey);
            redisTemplate.opsForValue().decrement(getVodLikeDeltaKey(broadcastId));
            markVodDirty(broadcastId);
            return false;
        }

        redisTemplate.opsForSet().add(key, memberKey);
        redisTemplate.opsForValue().increment(getVodLikeDeltaKey(broadcastId));
        markVodDirty(broadcastId);
        return true;
    }

    public int getVodLikeDelta(Long broadcastId) {
        return getInt(getVodLikeDeltaKey(broadcastId));
    }

    public int getVodViewDelta(Long broadcastId) {
        return getInt(getVodViewDeltaKey(broadcastId));
    }

    public boolean reportVod(Long broadcastId, Long memberId) {
        String userKey = getReportUsersKey(broadcastId);
        String memberKey = String.valueOf(memberId);

        Long added = redisTemplate.opsForSet().add(userKey, memberKey);
        if (added != null && added == 1) {
            redisTemplate.opsForValue().increment(getVodReportDeltaKey(broadcastId));
            markVodDirty(broadcastId);
            return true;
        }

        return false;
    }

    public int getVodReportDelta(Long broadcastId) {
        return getInt(getVodReportDeltaKey(broadcastId));
    }

    public VodStatsDelta consumeVodStats(Long broadcastId) {
        int viewDelta = consumeDelta(getVodViewDeltaKey(broadcastId));
        int likeDelta = consumeDelta(getVodLikeDeltaKey(broadcastId));
        int reportDelta = consumeDelta(getVodReportDeltaKey(broadcastId));
        return new VodStatsDelta(viewDelta, likeDelta, reportDelta);
    }

    public java.util.Set<Long> getDirtyVodIds() {
        var members = redisTemplate.opsForSet().members(getVodStatsDirtyKey());
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .map(Object::toString)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    public void markVodDirty(Long broadcastId) {
        redisTemplate.opsForSet().add(getVodStatsDirtyKey(), String.valueOf(broadcastId));
    }

    public void saveMediaConfig(Long broadcastId, Long sellerId, String cameraId, String microphoneId, boolean cameraOn, boolean microphoneOn, int volume) {
        String key = getMediaConfigKey(broadcastId, sellerId);
        redisTemplate.opsForHash().put(key, "cameraId", cameraId);
        redisTemplate.opsForHash().put(key, "microphoneId", microphoneId);
        redisTemplate.opsForHash().put(key, "cameraOn", String.valueOf(cameraOn));
        redisTemplate.opsForHash().put(key, "microphoneOn", String.valueOf(microphoneOn));
        redisTemplate.opsForHash().put(key, "volume", String.valueOf(volume));
        redisTemplate.expire(key, Duration.ofDays(1));
    }

    public List<Object> getMediaConfig(Long broadcastId, Long sellerId) {
        String key = getMediaConfigKey(broadcastId, sellerId);
        return redisTemplate.opsForHash().multiGet(key, List.of("cameraId", "microphoneId", "cameraOn", "microphoneOn", "volume"));
    }

    public int getMaxViewers(Long broadcastId) {
        return getInt(getMaxViewersKey(broadcastId));
    }

    public LocalDateTime getMaxViewersTime(Long broadcastId) {
        Object val = redisTemplate.opsForValue().get(getMaxViewersTimeKey(broadcastId));
        return val != null ? LocalDateTime.parse(val.toString()) : null;
    }

    public void increment(String key) {
        redisTemplate.opsForValue().increment(key);
    }

    public Integer getStatOrZero(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void publish(String channelTopic, Object message) {
        redisTemplate.convertAndSend(channelTopic, message);
    }

    public void deleteBroadcastKeys(Long broadcastId) {
        redisTemplate.delete(getRealtimeViewKey(broadcastId));
        redisTemplate.delete(getSessionCountKey(broadcastId));
        redisTemplate.delete(getTotalUvKey(broadcastId));
        redisTemplate.delete(getLikeUsersKey(broadcastId));
        redisTemplate.delete(getSanctionKey(broadcastId));
        redisTemplate.delete(getReportUsersKey(broadcastId));
        redisTemplate.delete(getReportCountKey(broadcastId));
        redisTemplate.delete(getMaxViewersKey(broadcastId));
        redisTemplate.delete(getMaxViewersTimeKey(broadcastId));
    }

    public void deleteBroadcastRuntimeKeys(Long broadcastId) {
        redisTemplate.delete(getRealtimeViewKey(broadcastId));
        redisTemplate.delete(getSessionCountKey(broadcastId));
        redisTemplate.delete(getTotalUvKey(broadcastId));
        redisTemplate.delete(getSanctionKey(broadcastId));
        redisTemplate.delete(getMaxViewersKey(broadcastId));
        redisTemplate.delete(getMaxViewersTimeKey(broadcastId));
    }

    public void persistVodReactionKeys(Long broadcastId) {
        redisTemplate.persist(getLikeUsersKey(broadcastId));
        redisTemplate.persist(getReportUsersKey(broadcastId));
        redisTemplate.persist(getReportCountKey(broadcastId));
    }

    public void deleteVodKeys(Long broadcastId) {
        redisTemplate.delete(getLikeUsersKey(broadcastId));
        redisTemplate.delete(getReportUsersKey(broadcastId));
        redisTemplate.delete(getReportCountKey(broadcastId));
        redisTemplate.delete(getVodViewersKey(broadcastId));
        redisTemplate.delete(getVodViewDeltaKey(broadcastId));
        redisTemplate.delete(getVodLikeDeltaKey(broadcastId));
        redisTemplate.delete(getVodReportDeltaKey(broadcastId));
        redisTemplate.opsForSet().remove(getVodStatsDirtyKey(), String.valueOf(broadcastId));
    }

    private void expireKey(String key) {
        redisTemplate.expire(key, Duration.ofDays(1));
    }

    public void updatePeakViewers(Long broadcastId) {
        int current = getRealtimeViewerCount(broadcastId);
        String maxKey = getMaxViewersKey(broadcastId);

        Integer max = getInt(maxKey);

        if (current > max) {
            redisTemplate.opsForValue().set(maxKey, String.valueOf(current));
            redisTemplate.opsForValue().set(getMaxViewersTimeKey(broadcastId), LocalDateTime.now().toString());
        }
    }

    private int getInt(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int consumeDelta(String key) {
        Object value = redisTemplate.opsForValue().getAndSet(key, 0);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
