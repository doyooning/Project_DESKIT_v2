package com.deskit.deskit.livechat.service;

import com.deskit.deskit.livechat.dto.LiveChatCacheEntry;
import com.deskit.deskit.livechat.dto.LiveChatMessageDTO;
import com.deskit.deskit.livechat.dto.LiveMessageType;
import com.deskit.deskit.livechat.entity.LiveChat;
import com.deskit.deskit.livechat.repository.ForbiddenWordRepository;
import com.deskit.deskit.livechat.repository.LiveChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LiveChatService {
    private static final long DEFAULT_RECENT_WINDOW_SECONDS = 60L;
    private static final String RECENT_CHAT_KEY_PREFIX = "livechat:recent:";

    private final LiveChatRepository liveChatRepository;
    private final ForbiddenWordRepository forbiddenWordRepository;
    private final ObjectMapper objectMapper;
    @Qualifier("chatRedisTemplate")
    private final RedisTemplate<String, Object> chatRedisTemplate;
    private Trie forbiddenTrie;

    @PostConstruct
    public void init() {
        List<String> words = forbiddenWordRepository.findAll()
                .stream()
                .map(word -> word.getWord())
                .filter(word -> word != null && !word.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (words.isEmpty()) {
            this.forbiddenTrie = null;
            return;
        }
        this.forbiddenTrie = Trie.builder()
                .addKeywords(words)
                .build();
    }

    public String filterContent(String content) {
        if (content == null || forbiddenTrie == null) {
            return content;
        }
        Collection<Emit> rawEmits = forbiddenTrie.parseText(content);
        if (rawEmits == null || rawEmits.isEmpty()) {
            return content;
        }
        List<Emit> emits = new ArrayList<>(rawEmits);
        emits.sort(Comparator.comparingInt(Emit::getStart)
                .thenComparing(Comparator.comparingInt(Emit::getEnd).reversed()));
        StringBuilder result = new StringBuilder(content.length());
        int cursor = 0;
        int lastEnd = -1;
        for (Emit emit : emits) {
            int start = emit.getStart();
            int end = emit.getEnd();
            if (start <= lastEnd) {
                continue;
            }
            if (start > cursor) {
                result.append(content, cursor, start);
            }
            result.append("***");
            cursor = end + 1;
            lastEnd = end;
        }
        if (cursor < content.length()) {
            result.append(content.substring(cursor));
        }
        return result.toString();
    }

    @Async("chatSaveExecutor")
    public void saveMessageAsync(LiveChatMessageDTO dto) {
        String rawContent = dto.getRawContent() != null ? dto.getRawContent() : dto.getContent();
        LiveChat entity = LiveChat.builder()
                .broadcastId(dto.getBroadcastId())
                .memberEmail(dto.getMemberEmail())
                .msgType(dto.getType())
                .content(dto.getContent())
                .rawContent(rawContent)
                .sendNick(dto.getSender())
                .isWorld(dto.isWorld())
                .vodPlayTime(dto.getVodPlayTime())
                .build();

        liveChatRepository.save(entity);
        log.debug("livechat.db.saved broadcastId={} messageId={}",
                dto.getBroadcastId(),
                entity.getMessageId());
    }

    public void cacheRecentMessage(LiveChatMessageDTO dto) {
        if (dto == null || dto.getBroadcastId() == null) {
            return;
        }
        if (dto.getType() != LiveMessageType.TALK) {
            return;
        }
        long now = System.currentTimeMillis();
        if (dto.getSentAt() == null) {
            dto.setSentAt(now);
        }
        LiveChatCacheEntry entry = LiveChatCacheEntry.builder()
                .broadcastId(dto.getBroadcastId())
                .memberEmail(dto.getMemberEmail())
                .type(dto.getType())
                .sender(dto.getSender())
                .content(dto.getContent())
                .senderRole(dto.getSenderRole())
                .connectionId(dto.getConnectionId())
                .vodPlayTime(dto.getVodPlayTime())
                .sentAt(dto.getSentAt())
                .build();

        String key = recentChatKey(dto.getBroadcastId());
        chatRedisTemplate.opsForZSet().add(key, entry, dto.getSentAt());
        long cutoff = now - (DEFAULT_RECENT_WINDOW_SECONDS * 1000L);
        chatRedisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        log.debug("livechat.cache.saved broadcastId={} sentAt={}", dto.getBroadcastId(), dto.getSentAt());
    }

    public List<LiveChatMessageDTO> getRecentTalks(Long broadcastId, Long seconds) {
        if (broadcastId == null) {
            return Collections.emptyList();
        }
        long windowSeconds = seconds == null
                ? DEFAULT_RECENT_WINDOW_SECONDS
                : Math.min(DEFAULT_RECENT_WINDOW_SECONDS, Math.max(1L, seconds));
        long now = System.currentTimeMillis();
        long cutoff = now - (windowSeconds * 1000L);

        String key = recentChatKey(broadcastId);
        Set<Object> raw = chatRedisTemplate.opsForZSet().rangeByScore(key, cutoff, now);
        if (raw == null || raw.isEmpty()) {
            log.debug("livechat.cache.miss broadcastId={}", broadcastId);
            return Collections.emptyList();
        }

        List<LiveChatMessageDTO> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            LiveChatCacheEntry entry = toCacheEntry(item);
            if (entry == null || entry.getType() != LiveMessageType.TALK) {
                continue;
            }
            result.add(LiveChatMessageDTO.builder()
                    .broadcastId(entry.getBroadcastId())
                    .memberEmail(entry.getMemberEmail())
                    .type(entry.getType())
                    .sender(entry.getSender())
                    .content(entry.getContent())
                    .senderRole(entry.getSenderRole())
                    .connectionId(entry.getConnectionId())
                    .vodPlayTime(entry.getVodPlayTime())
                    .sentAt(entry.getSentAt())
                    .build());
        }
        log.debug("livechat.cache.hit broadcastId={} count={}", broadcastId, result.size());
        return result;
    }

    private LiveChatCacheEntry toCacheEntry(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof LiveChatCacheEntry) {
            return (LiveChatCacheEntry) item;
        }
        try {
            return objectMapper.convertValue(item, LiveChatCacheEntry.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String recentChatKey(Long broadcastId) {
        return RECENT_CHAT_KEY_PREFIX + broadcastId;
    }
}
