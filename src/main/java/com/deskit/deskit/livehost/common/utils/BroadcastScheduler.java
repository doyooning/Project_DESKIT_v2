package com.deskit.deskit.livehost.common.utils;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.repository.BroadcastRepository;
import com.deskit.deskit.livehost.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastScheduler {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastService broadcastService;

    @Scheduled(cron = "0 * * * * *")
    public void autoClose() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);

        List<Broadcast> targets = broadcastRepository.findByStatusAndStartedAtBefore(BroadcastStatus.ON_AIR, threshold);

        for (Broadcast broadcast : targets) {
            try {
                log.info("방송 시간(30분) 초과로 자동 종료: id={}", broadcast.getBroadcastId());
                broadcastService.endBroadcast(broadcast.getSeller().getSellerId(), broadcast.getBroadcastId());
            } catch (Exception e) {
                log.error("자동 종료 실패: id={}, msg={}", broadcast.getBroadcastId(), e.getMessage());
            }
        }
    }
}
