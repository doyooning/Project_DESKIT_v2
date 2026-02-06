package com.deskit.deskit.livehost.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SanctionStatisticsResponse {

    // 1. 차트 데이터 (일/월/년)
    private List<ChartData> forceStopChart;   // 판매자 강제 종료 건수 추이
    private List<ChartData> viewerBanChart;   // 시청자 제재 건수 추이

    // 2. 랭킹 리스트
    private List<SellerRank> worstSellers;    // 제재 많이 당한 판매자 순위
    private List<ViewerRank> worstViewers;    // 제재 많이 당한 시청자 순위

    @Getter @AllArgsConstructor
    public static class ChartData {
        private String label; // 날짜 (X축)
        private Long count;   // 건수 (Y축)
    }

    @Getter @Builder
    public static class SellerRank {
        private Long sellerId;
        private String sellerName; // 브랜드명
        private String email;
        private Long sanctionCount;
    }

    @Getter @Builder
    public static class ViewerRank {
        private String viewerId;   // 회원ID 또는 UUID
        private String name;   // (회원인 경우)
        private Long sanctionCount;
    }
}
