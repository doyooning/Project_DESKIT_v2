package com.deskit.deskit.livechat.entity;

import com.deskit.deskit.livechat.dto.LiveMessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_chat")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LiveChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(nullable = false,name = "broadcast_id")
    private Long broadcastId;

    @Column(nullable = false,name = "member_email")
    private String memberEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,name = "msg_type")
    private LiveMessageType msgType;

    @Column(length = 500, nullable = false,name = "content")
    private String content;

    @Column(length = 500, nullable = false,name = "raw_content")
    private String rawContent;

    @Column(length = 50, nullable = false,name = "send_nick")
    private String sendNick;

    @Column(nullable = false,name = "is_world")
    private boolean isWorld;

    @CreationTimestamp
    @Column(nullable = false, updatable = false,name = "send_lchat")
    private LocalDateTime sendLchat;

    @Column(nullable = false,name = "vod_play_time")
    private int vodPlayTime;
}
