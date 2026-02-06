package com.deskit.deskit.livehost.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BroadcastProductStatus {
    SELLING("판매 중"),
    SOLDOUT("품절"),
    DELETED("삭제됨");

    private final String description;
}

