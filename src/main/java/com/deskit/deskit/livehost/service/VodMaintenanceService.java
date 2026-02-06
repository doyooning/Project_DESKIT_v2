package com.deskit.deskit.livehost.service;

import com.deskit.deskit.livehost.common.enums.VodStatus;
import com.deskit.deskit.livehost.entity.Vod;
import com.deskit.deskit.livehost.repository.VodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VodMaintenanceService {

    private final VodRepository vodRepository;
    private final AwsS3Service s3Service;
    private final VodStatsService vodStatsService;
    private final RedisService redisService;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredVods() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        List<Vod> expired = vodRepository.findByStatusNotAndCreatedAtBefore(VodStatus.DELETED, threshold);
        if (expired.isEmpty()) {
            return;
        }
        for (Vod vod : expired) {
            try {
                if (vod.getVodUrl() != null && !vod.getVodUrl().isBlank()) {
                    s3Service.deleteObjectByUrl(vod.getVodUrl());
                }
                Long broadcastId = vod.getBroadcast().getBroadcastId();
                vodStatsService.flushVodStats(broadcastId);
                redisService.deleteVodKeys(broadcastId);
                vod.markDeleted();
            } catch (Exception e) {
                log.error("VOD 자동 삭제 실패: vodId={}", vod.getVodId(), e);
            }
        }
    }
}
