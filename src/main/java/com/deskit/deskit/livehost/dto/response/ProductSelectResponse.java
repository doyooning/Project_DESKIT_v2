package com.deskit.deskit.livehost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductSelectResponse {
    private Long productId;
    private String productName;
    private Integer price;      // 판매가
    private Integer stockQty;   // 현재 재고
    private Integer safetyStock;   // 안전 재고
    private Integer reservedBroadcastQty;   // 예약 방송 판매 수량
    private String imageUrl;    // 대표 이미지 URL
}
