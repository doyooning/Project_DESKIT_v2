package com.deskit.deskit.livehost.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsResponse {
    private List<ChartData> salesChart;      // 매출 차트
    private List<ChartData> arpuChart;       // 객단가 차트

    private List<BroadcastRank> bestBroadcasts;  // 매출 Best
    private List<BroadcastRank> worstBroadcasts; // 매출 Worst
    private List<BroadcastRank> topViewerBroadcasts; // 시청자수 Best (판매자용)
    private List<BroadcastRank> worstViewerBroadcasts; // 시청자수 Worst (판매자용)
    private List<ProductRank> bestProducts;     // 상품 매출 Best (관리자용)
    private List<ProductRank> worstProducts;    // 상품 매출 Worst (관리자용)

    @Getter
    @AllArgsConstructor
    public static class ChartData {
        private String label;
        private BigDecimal value;
    }
    @Getter
    @Builder
    public static class BroadcastRank {
        private Long broadcastId;
        private String title;
        private BigDecimal totalSales;
        private int totalViews;
    }

    @Getter
    @Builder
    public static class ProductRank {
        private Long productId;
        private String title;
        private BigDecimal totalSales;
    }
}
