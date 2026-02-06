package com.deskit.deskit.livechat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveChatMessageDTO {
    private Long broadcastId;
    private String memberEmail;
    private LiveMessageType type;
    private String sender;
    private String content;
    private boolean isWorld;
    private String senderRole;
    private String connectionId;
    @JsonIgnore
    private String rawContent;
    private int vodPlayTime;
    private Long sentAt;
}
