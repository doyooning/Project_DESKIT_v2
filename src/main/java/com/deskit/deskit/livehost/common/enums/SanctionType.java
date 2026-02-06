package com.deskit.deskit.livehost.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SanctionType {
    MUTE("채팅 금지"),
    OUT("강제 퇴장");

    private final String description;
}
