package com.deskit.deskit.livehost.service;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.dto.response.SanctionStatisticsResponse;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.repository.BroadcastRepository;
import com.deskit.deskit.livehost.repository.SanctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final SanctionRepository sanctionRepository;
    private final BroadcastRepository broadcastRepository;
    private final OpenViduService openViduService;
    private final RedisService redisService;
    private final SseService sseService;
    private final SanctionService sanctionService;
    private final BroadcastService broadcastService;

    @Transactional(readOnly = true)
    public SanctionStatisticsResponse getSanctionStatistics(String period) {
        return SanctionStatisticsResponse.builder()
                .forceStopChart(sanctionRepository.getSellerForceStopChart(period))
                .viewerBanChart(sanctionRepository.getViewerSanctionChart(period))
                .worstSellers(sanctionRepository.getSellerForceStopRanking(period, 5))
                .worstViewers(sanctionRepository.getViewerSanctionRanking(period, 5))
                .build();
    }

    @Transactional
    public void forceStopBroadcast(Long broadcastId, String reason) {
        String lockKey = "lock:broadcast_transition:" + broadcastId;
        if (!Boolean.TRUE.equals(redisService.acquireLock(lockKey, 3000))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        try {
            Broadcast broadcast = broadcastRepository.findById(broadcastId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            validateTransition(broadcast.getStatus(), BroadcastStatus.STOPPED);
            broadcast.forceStopByAdmin(reason);
            broadcastService.restoreOriginalProductPrice(broadcast);

            broadcastService.saveBroadcastResultSnapshot(broadcast);
            openViduService.closeSession(broadcastId);
            redisService.deleteBroadcastRuntimeKeys(broadcastId);
            sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_STOPPED", reason);
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    @Transactional
    public void cancelBroadcast(Long broadcastId, String reason) {
        String lockKey = "lock:broadcast_transition:" + broadcastId;
        if (!Boolean.TRUE.equals(redisService.acquireLock(lockKey, 3000))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        try {
            Broadcast broadcast = broadcastRepository.findById(broadcastId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

            if (broadcast.getStatus() != BroadcastStatus.RESERVED) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            validateTransition(broadcast.getStatus(), BroadcastStatus.CANCELED);
            broadcast.cancelBroadcast(reason);
            sseService.notifyBroadcastUpdate(broadcastId, "BROADCAST_CANCELED", reason);
        } finally {
            redisService.releaseLock(lockKey);
        }
    }

    private void validateTransition(BroadcastStatus from, BroadcastStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new BusinessException(ErrorCode.BROADCAST_INVALID_TRANSITION);
        }
    }

    private boolean isTransitionAllowed(BroadcastStatus from, BroadcastStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case RESERVED -> to == BroadcastStatus.READY || to == BroadcastStatus.CANCELED;
            case READY -> to == BroadcastStatus.ON_AIR || to == BroadcastStatus.CANCELED || to == BroadcastStatus.STOPPED;
            case ON_AIR -> to == BroadcastStatus.ENDED || to == BroadcastStatus.STOPPED;
            case ENDED -> to == BroadcastStatus.VOD || to == BroadcastStatus.STOPPED;
            case STOPPED -> to == BroadcastStatus.VOD;
            default -> false;
        };
    }

    @Transactional
    public void sanctionViewer(Long adminId, Long broadcastId, com.deskit.deskit.livehost.dto.request.SanctionRequest request) {
        sanctionService.sanctionUserByAdmin(adminId, broadcastId, request);
    }
}
