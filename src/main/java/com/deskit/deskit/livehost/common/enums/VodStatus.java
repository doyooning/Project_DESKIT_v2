package com.deskit.deskit.livehost.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VodStatus {
    PUBLIC("공개"),
    PRIVATE("비공개"),
    DELETED("삭제됨");

    private final String description;
}
