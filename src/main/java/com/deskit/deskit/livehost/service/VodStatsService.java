package com.deskit.deskit.livehost.service;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.entity.BroadcastResult;
import com.deskit.deskit.livehost.entity.Vod;
import com.deskit.deskit.livehost.repository.BroadcastRepository;
import com.deskit.deskit.livehost.repository.BroadcastResultRepository;
import com.deskit.deskit.livehost.repository.VodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VodStatsService {
    private final RedisService redisService;
    private final BroadcastRepository broadcastRepository;
    private final BroadcastResultRepository broadcastResultRepository;
    private final VodRepository vodRepository;

    @Transactional
    public void flushVodStats(Long broadcastId) {
        VodStatsDelta delta = redisService.consumeVodStats(broadcastId);
        if (delta.isEmpty()) {
            return;
        }

        Broadcast broadcast = broadcastRepository.findById(broadcastId).orElse(null);
        if (broadcast == null || broadcast.getStatus() != BroadcastStatus.VOD) {
            return;
        }

        BroadcastResult result = broadcastResultRepository.findById(broadcastId).orElse(null);
        if (result == null) {
            result = BroadcastResult.builder()
                    .broadcast(broadcast)
                    .totalViews(0)
                    .totalLikes(0)
                    .totalReports(0)
                    .avgWatchTime(0)
                    .maxViews(0)
                    .pickViewsAt(resolveMaxViewsAt(broadcast))
                    .totalChats(0)
                    .totalSales(BigDecimal.ZERO)
                    .build();
        }

        result.applyVodStatsDelta(delta.viewDelta(), delta.likeDelta(), delta.reportDelta());
        broadcastResultRepository.save(result);

        if (delta.reportDelta() != 0) {
            Vod vod = vodRepository.findByBroadcast(broadcast).orElse(null);
            if (vod != null) {
                vod.applyReportDelta(delta.reportDelta());
            }
        }
    }

    private LocalDateTime resolveMaxViewsAt(Broadcast broadcast) {
        if (broadcast.getStartedAt() != null) {
            return broadcast.getStartedAt();
        }
        if (broadcast.getCreatedAt() != null) {
            return broadcast.getCreatedAt();
        }
        return LocalDateTime.now();
    }
}
