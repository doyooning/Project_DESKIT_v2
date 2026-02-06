package com.deskit.deskit.livehost.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BroadcastStatus {
    RESERVED("방송 예약"),
    READY("방송 대기"),
    ON_AIR("방송 중"),
    ENDED("방송 종료"),
    VOD("VOD"),
    CANCELED("예약 취소"),
    STOPPED("강제 중지"),
    DELETED("삭제됨");

    private final String description;
}
