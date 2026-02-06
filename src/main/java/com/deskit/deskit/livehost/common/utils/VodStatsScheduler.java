package com.deskit.deskit.livehost.common.utils;

import com.deskit.deskit.livehost.service.RedisService;
import com.deskit.deskit.livehost.service.VodStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VodStatsScheduler {
    private final RedisService redisService;
    private final VodStatsService vodStatsService;

    @Scheduled(fixedDelay = 10000)
    public void flushVodStats() {
        var broadcastIds = redisService.getDirtyVodIds();
        if (broadcastIds.isEmpty()) {
            return;
        }

        for (Long broadcastId : broadcastIds) {
            try {
                vodStatsService.flushVodStats(broadcastId);
            } catch (Exception e) {
                log.error("VOD 통계 반영 실패: broadcastId={}, msg={}", broadcastId, e.getMessage());
            }
        }
    }
}
