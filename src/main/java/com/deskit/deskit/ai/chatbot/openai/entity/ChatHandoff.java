package com.deskit.deskit.ai.chatbot.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "chat_handoff")
public class ChatHandoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "handoff_id")
    private Long handoffId;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HandoffStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}
