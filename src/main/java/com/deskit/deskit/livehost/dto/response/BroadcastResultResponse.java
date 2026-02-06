package com.deskit.deskit.livehost.dto.response;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.common.enums.VodStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class BroadcastResultResponse {
    private Long broadcastId;
    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    private long durationMinutes;
    private BroadcastStatus status;
    private String stoppedReason;

    // 통계 정보
    private int totalViews;
    private int totalLikes;
    private BigDecimal totalSales;
    private int totalChats;
    private int maxViewers;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime maxViewerTime;
    private long avgWatchTime;
    private int reportCount;
    private int sanctionCount;

    // vod
    private String vodUrl;
    private VodStatus vodStatus;
    private boolean vodAdminLock;
    private boolean isEncoding;

    private List<ProductSalesStat> productStats;

    @Getter @Builder
    public static class ProductSalesStat {
        private Long productId;
        private String productName;
        private String imageUrl;
        private int price;
        private int salesQuantity;
        private BigDecimal salesAmount;
    }
}
