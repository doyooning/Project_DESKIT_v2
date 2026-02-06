package com.deskit.deskit.directchat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DirectChatSummaryResponse {

    private Long chatId;
    private Long memberId;
    private String loginId;
    private String status;
    private LocalDateTime createdAt;
    private Long assignedAdminId;
    private String handoffStatus;
}
