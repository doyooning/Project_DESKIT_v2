package com.deskit.deskit.livehost.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class BroadcastAllResponse {
    private List<BroadcastListResponse> onAir;        // 방송 중 (5개-판매자 1개)
    private List<BroadcastListResponse> reserved; // 예약 (5개)
    private List<BroadcastListResponse> vod;      // VOD (5개)
}