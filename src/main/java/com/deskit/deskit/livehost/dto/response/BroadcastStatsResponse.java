package com.deskit.deskit.livehost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastStatsResponse {
    private int viewerCount; // 시청자 수
    private int likeCount;   // 좋아요 수
    private int reportCount; // 신고 수
}