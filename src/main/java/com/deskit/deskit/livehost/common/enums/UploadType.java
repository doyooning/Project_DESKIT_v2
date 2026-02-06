package com.deskit.deskit.livehost.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UploadType {

    // 썸네일: 9:16 비율, 최대 5MB
    THUMBNAIL(5 * 1024 * 1024L, 16.0, 9.0, "방송 썸네일"),

    // 대기화면: 16:9 비율, 최대 7MB
    WAIT_SCREEN(5 * 1024 * 1024L, 16.0, 9.0, "방송 대기화면"),

    // 상품 이미지: 1:1 비율, 최대 5MB
    PRODUCT_IMAGE(5 * 1024 * 1024L, 1.0, 1.0, "상품 이미지");

    private final long maxSizeBytes; // 최대 용량 (Byte)
    private final double widthRatio; // 가로 비율
    private final double heightRatio; // 세로 비율
    private final String description;

    // 목표 비율 계산 (예: 9/16 = 0.5625)
    public double getTargetRatio() {
        return widthRatio / heightRatio;
    }
}
