package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.dto.response.StatisticsResponse;

import java.util.List;

public interface BroadcastResultRepositoryCustom {
    List<StatisticsResponse.ChartData> getSalesChart(Long sellerId, String periodType);

    List<StatisticsResponse.ChartData> getArpuChart(Long sellerId, String periodType);

    List<StatisticsResponse.BroadcastRank> getRanking(Long sellerId, String periodType, String sortField, boolean isDesc, int limit);
}
