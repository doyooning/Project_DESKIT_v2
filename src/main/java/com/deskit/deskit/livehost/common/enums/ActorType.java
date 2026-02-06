package com.deskit.deskit.livehost.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActorType {
    ADMIN("관리자"),
    SELLER("판매자");

    private final String description;
}
