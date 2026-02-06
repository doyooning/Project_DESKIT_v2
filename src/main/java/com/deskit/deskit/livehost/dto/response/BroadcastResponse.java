package com.deskit.deskit.livehost.dto.response;

import com.deskit.deskit.livehost.common.enums.BroadcastLayout;
import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class BroadcastResponse {
    private Long broadcastId;

    private Long sellerId;
    private String sellerName;
    private String sellerProfileUrl;

    private String title;
    private String notice;
    private BroadcastStatus status;
    private BroadcastLayout layout;
    private Long categoryId;
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    private String thumbnailUrl;
    private String waitScreenUrl;
    private String stoppedReason;

    private String streamKey; // OpenVidu Session ID
    private String vodUrl;    // Viewer용 VOD 재생 URL

    // 통계 (조회 시점 기준)
    private Integer totalViews;
    private Integer totalLikes;
    private Integer totalReports;

    // 연관 데이터 리스트
    private List<BroadcastProductResponse> products;
    private List<QcardResponse> qcards;

    // [Helper] Factory Method
    public static BroadcastResponse fromEntity(Broadcast broadcast,
                                               String categoryName,
                                               Integer totalViews,
                                               Integer totalLikes,
                                               Integer totalReports,
                                               List<BroadcastProductResponse> products,
                                               List<QcardResponse> qcards,
                                               String vodUrl) { // vodUrl 파라미터 추가
        return BroadcastResponse.builder()
                .broadcastId(broadcast.getBroadcastId())
                // 판매자 정보 매핑
                .sellerId(broadcast.getSeller().getSellerId())
                .sellerName(broadcast.getSeller().getName())     // Entity에서 조회
                .sellerProfileUrl(broadcast.getSeller().getProfile()) // Entity에서 조회 (필드명 확인 필요)

                .title(broadcast.getBroadcastTitle())
                .notice(broadcast.getBroadcastNotice())
                .status(broadcast.getStatus())
                .layout(broadcast.getBroadcastLayout())
                .categoryId(broadcast.getTagCategory().getId())
                .categoryName(categoryName)

                .scheduledAt(broadcast.getScheduledAt())
                .startedAt(broadcast.getStartedAt())
                .thumbnailUrl(broadcast.getBroadcastThumbUrl())
                .waitScreenUrl(broadcast.getBroadcastWaitUrl())
                .stoppedReason(broadcast.getBroadcastStoppedReason())

                .streamKey(broadcast.getStreamKey()) // 라이브 시청용 (Session ID)
                .vodUrl(vodUrl)                      // VOD 시청용

                .totalViews(totalViews != null ? totalViews : 0)
                .totalLikes(totalLikes != null ? totalLikes : 0)
                .totalReports(totalReports != null ? totalReports : 0)

                .products(products)
                .qcards(qcards)
                .build();
    }
}
