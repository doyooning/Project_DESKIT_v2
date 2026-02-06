package com.deskit.deskit.livehost.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "view_history", indexes = @Index(name = "idx_bh_broadcast_viewer", columnList = "broadcast_id, viewer_id"))
public class ViewHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private Broadcast broadcast;

    // 비회원도 집계하기 위해 viewerId(String) 사용
    @Column(name = "viewer_id", nullable = false, length = 100)
    private String viewerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private ViewHistory(Broadcast broadcast, String viewerId) {
        this.broadcast = broadcast;
        this.viewerId = viewerId;
    }

    public static ViewHistory enter(Broadcast broadcast, String viewerId) {
        return new ViewHistory(broadcast, viewerId);
    }

    // 입장 시 createdAt과 동일하게 초기화
    @PrePersist
    public void prePersist() {
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    // 퇴장 시 호출하여 시간 갱신
    public void recordExit() {
        this.updatedAt = LocalDateTime.now();
    }
}
