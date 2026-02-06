package com.deskit.deskit.livehost.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BroadcastLayout {
    FULL("전체 화면"),
    LAYOUT_4("4분할 화면"),
    LAYOUT_3("3분할 화면"),
    LAYOUT_2("2분할 화면");

    private final String description;
}
