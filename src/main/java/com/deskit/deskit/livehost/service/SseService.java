package com.deskit.deskit.livehost.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long broadcastId, String userId) {
        String resolvedUserId = resolveUserId(userId);
        String key = buildBroadcastKey(broadcastId, resolvedUserId, UUID.randomUUID().toString());
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        emitters.put(key, emitter);

        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> {
            emitters.remove(key);
            emitter.complete();
        });
        emitter.onError((e) -> {
            emitters.remove(key);
            emitter.completeWithError(e);
        });

        sendToClient(emitter, key, "connect", "Connected!");

        return emitter;
    }

    public SseEmitter subscribeAll(String userId) {
        String resolvedUserId = resolveUserId(userId);
        String key = buildAllKey(resolvedUserId, UUID.randomUUID().toString());
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        emitters.put(key, emitter);

        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> {
            emitters.remove(key);
            emitter.complete();
        });
        emitter.onError((e) -> {
            emitters.remove(key);
            emitter.completeWithError(e);
        });

        sendToClient(emitter, key, "connect", "Connected!");

        return emitter;
    }

    public void notifyBroadcastUpdate(Long broadcastId, String eventName, Object data) {
        String prefix = broadcastId + ":";
        emitters.forEach((key, emitter) -> {
            if (key.startsWith(prefix)) {
                sendToClient(emitter, key, eventName, data);
            }
        });
        notifyGlobalUpdate(broadcastId, eventName, data);
    }

    public void notifyBroadcastUpdate(Long broadcastId, String eventName) {
        notifyBroadcastUpdate(broadcastId, eventName, "update");
    }

    public void notifyBroadcastUpdate(Long broadcastId) {
        notifyBroadcastUpdate(broadcastId, "BROADCAST_UPDATED", "info_changed");
    }

    public void notifyTargetUser(Long broadcastId, Long userId, String eventName, Object data) {
        String prefix = broadcastId + ":" + userId + ":";
        boolean delivered = false;
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                sendToClient(entry.getValue(), key, eventName, data);
                delivered = true;
            }
        }
        if (!delivered) {
            log.warn("Target user not found or disconnected: keyPrefix={}", prefix);
        }
    }

    public void notifyTargetUser(Long broadcastId, String userId, String eventName, Object data) {
        if (userId == null || userId.isBlank()) {
            log.warn("Skip target notify due to empty userId: broadcastId={}, eventName={}", broadcastId, eventName);
            return;
        }
        String resolvedUserId = resolveUserId(userId);
        String prefix = broadcastId + ":" + resolvedUserId + ":";
        boolean delivered = false;
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                sendToClient(entry.getValue(), key, eventName, data);
                delivered = true;
            }
        }
        if (!delivered) {
            log.warn("Target user not found or disconnected: keyPrefix={}", prefix);
        }
    }

    private void notifyGlobalUpdate(Long broadcastId, String eventName, Object data) {
        Map<String, Object> payload = Map.of(
                "broadcastId", broadcastId,
                "payload", data
        );
        emitters.forEach((key, emitter) -> {
            if (key.startsWith("ALL:")) {
                sendToClient(emitter, key, eventName, payload);
            }
        });
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        emitters.forEach((key, emitter) -> sendToClient(emitter, key, "PING", "ping"));
    }

    private void sendToClient(SseEmitter emitter, String id, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().id(id).name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(id);
            emitter.complete();
            log.debug("SSE connection closed: key={}, reason={}", id, e.getMessage());
        }
    }

    private String resolveUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "anonymous";
        }
        return userId;
    }

    private String buildBroadcastKey(Long broadcastId, String userId, String sessionId) {
        return broadcastId + ":" + userId + ":" + sessionId;
    }

    private String buildAllKey(String userId, String sessionId) {
        return "ALL:" + userId + ":" + sessionId;
    }
}
