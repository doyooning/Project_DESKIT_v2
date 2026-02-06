package com.deskit.deskit.livehost.dto.response;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.common.enums.VodStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastListResponse {
    // 기본 정보
    private Long broadcastId;
    private String title;
    private String notice;
    private String sellerName;
    private String categoryName;
    private String thumbnailUrl;
    private BroadcastStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endAt;

    // 통계 정보
    private int viewerCount;       // (Redis/DB) VOD 누적 조회수
    private int liveViewerCount;   // (Redis) 실시간 시청자 수
    private long reportCount;      // (Redis/DB) 신고 수
    private BigDecimal totalSales; // (DB) 총 매출
    private int totalLikes;        // (Redis/DB) 좋아요 수

    private boolean isPublic;      // VOD 공개 여부
    private boolean adminLock;     // 관리자 제재 여부

    // 판매자 대시보드용 추가 정보
    private List<SimpleProductInfo> products; // 실시간 상품 재고 리스트

    // 내부 클래스: 상품 정보 요약
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SimpleProductInfo {
        private String name;
        private int stock;
        private boolean isSoldOut;
    }

    // QueryDSL Projection용 생성자
    public BroadcastListResponse(Long broadcastId, String title, String notice,
                                 String sellerName, String categoryName, String thumbnailUrl,
                                 BroadcastStatus status, LocalDateTime scheduledAt,
                                 LocalDateTime startedAt, LocalDateTime endedAt,
                                 Integer totalViews, VodStatus vodStatus, Long reportCount, // Long으로 변경
                                 BigDecimal totalSales, Integer totalLikes) {
        this.broadcastId = broadcastId;
        this.title = title;
        this.notice = notice;
        this.sellerName = sellerName;
        this.categoryName = categoryName;
        this.thumbnailUrl = thumbnailUrl;
        this.status = status;

        // 통계 null 처리
        this.reportCount = reportCount != null ? reportCount : 0L;
        this.totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
        this.totalLikes = totalLikes != null ? totalLikes : 0;
        this.viewerCount = totalViews != null ? totalViews : 0;

        // 시간 매핑 로직
        if (status == BroadcastStatus.RESERVED) {
            this.startAt = scheduledAt;
            this.endAt = (scheduledAt != null) ? scheduledAt.plusMinutes(30) : null;
        } else if (status == BroadcastStatus.ON_AIR) {
            this.startAt = (startedAt != null) ? startedAt : scheduledAt;
            this.endAt = null; // 진행 중이라 끝나는 시간 없음
        } else if (status == BroadcastStatus.READY) {
            this.startAt = scheduledAt;
            this.endAt = (scheduledAt != null) ? scheduledAt.plusMinutes(30) : null;
        } else if (status == BroadcastStatus.STOPPED) {
            this.startAt = (startedAt != null) ? startedAt : scheduledAt;
            if (scheduledAt != null) {
                this.endAt = scheduledAt.plusMinutes(30);
            } else {
                this.endAt = endedAt;
            }
        } else {
            this.startAt = startedAt;
            this.endAt = endedAt;
        }

        // VOD 공개 여부 및 락 설정
        this.isPublic = (vodStatus == VodStatus.PUBLIC);
        this.adminLock = (status == BroadcastStatus.STOPPED);

        // liveViewerCount와 products는 Service의 injectLiveDetails() 메서드에서 채워짐
    }
}
