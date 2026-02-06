package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.dto.request.BroadcastSearch;
import com.deskit.deskit.livehost.dto.response.BroadcastListResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.List;

public interface BroadcastRepositoryCustom {
    Slice<BroadcastListResponse> searchBroadcasts(Long sellerId, BroadcastSearch condition, Pageable pageable, boolean isAdmin);

    List<BroadcastListResponse> findTop5ByStatus(Long sellerId, List<BroadcastStatus> statuses, BroadcastSortOrder sortOrder, boolean isAdmin);

    long countByTimeSlot(LocalDateTime start, LocalDateTime end);

    List<Long> findBroadcastIdsForReadyTransition(LocalDateTime now);

    List<Long> findBroadcastIdsForNoShow(LocalDateTime now);

    List<BroadcastScheduleInfo> findBroadcastSchedules(LocalDateTime start, LocalDateTime end, List<BroadcastStatus> statuses);

    enum BroadcastSortOrder {
        STARTED_AT_DESC,
        SCHEDULED_AT_ASC,
        ENDED_AT_DESC
    }

    record BroadcastScheduleInfo(Long broadcastId, LocalDateTime scheduledAt, BroadcastStatus status) {
    }
}
