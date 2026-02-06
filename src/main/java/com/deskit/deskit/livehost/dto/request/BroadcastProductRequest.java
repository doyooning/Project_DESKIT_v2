package com.deskit.deskit.livehost.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BroadcastProductRequest {

    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @NotNull(message = "라이브 특가는 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Integer bpPrice;

    @NotNull(message = "방송 판매 수량은 필수입니다.")
    @Min(value = 1, message = "판매 수량은 최소 1개 이상이어야 합니다.")
    private Integer bpQuantity;
}
