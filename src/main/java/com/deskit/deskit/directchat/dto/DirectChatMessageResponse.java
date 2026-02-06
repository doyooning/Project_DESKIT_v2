package com.deskit.deskit.directchat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DirectChatMessageResponse {

    private Long messageId;
    private Long chatId;
    private String sender;
    private String content;
    private LocalDateTime createdAt;
}
