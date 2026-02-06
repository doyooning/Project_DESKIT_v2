package com.deskit.deskit.directchat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DirectChatLatestResponse {

    private Long chatId;
    private String status;
}
