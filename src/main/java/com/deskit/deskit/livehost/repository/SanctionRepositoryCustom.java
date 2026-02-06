package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.dto.response.SanctionStatisticsResponse;

import java.util.List;

public interface SanctionRepositoryCustom {

    List<SanctionStatisticsResponse.ChartData> getSellerForceStopChart(String periodType);

    List<SanctionStatisticsResponse.ChartData> getViewerSanctionChart(String periodType);

    List<SanctionStatisticsResponse.SellerRank> getSellerForceStopRanking(String periodType, int limit);

    List<SanctionStatisticsResponse.ViewerRank> getViewerSanctionRanking(String periodType, int limit);

    SanctionTypeResult findLatestSanction(Long broadcastId, Long memberId);

    record SanctionTypeResult(Long sanctionId, String status) {
    }
}
