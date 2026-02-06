package com.deskit.deskit.livechat.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveChatMessage {

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        CREATE // 방송(방) 생성 알림용
    }

    private MessageType type;
    private String content;
    private String sender;
    private String time;
    private String roomId;
}