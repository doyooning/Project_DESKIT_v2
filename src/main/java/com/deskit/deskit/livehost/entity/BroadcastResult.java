package com.deskit.deskit.livehost.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "broadcast_result")
public class BroadcastResult {

    @Id
    @Column(name = "broadcast_id")
    private Long broadcastId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // broadcastId를 PK로 사용하면서 FK로 설정
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    @Column(name = "total_views", nullable = false)
    private int totalViews;

    @Column(name = "max_views", nullable = false)
    private int maxViews;

    @Column(name = "max_views_at")
    private LocalDateTime pickViewsAt;

    @Column(name = "total_likes", nullable = false)
    private int totalLikes;

    @Column(name = "total_chats", nullable = false)
    private int totalChats;

    @Column(name = "total_sales", nullable = false, precision = 30, scale = 0)
    private BigDecimal totalSales; // 금액은 BigDecimal 사용 권장

    @Column(name = "avg_watch_time", nullable = false)
    private int avgWatchTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // 관리자용
    @Column(name = "total_reports", nullable = false)
    private int totalReports;

    public void updateFinalStats(int views, int likes, int reports, int avgWatchTime, int maxViews,
                                 LocalDateTime pickViewsAt, int totalChats, BigDecimal totalSales) {
        this.totalViews = views;
        this.totalLikes = likes;
        this.totalReports = reports;
        this.avgWatchTime = avgWatchTime;
        this.maxViews = maxViews;
        this.pickViewsAt = pickViewsAt;
        this.totalChats = totalChats;
        this.totalSales = totalSales;
    }

    public void updateTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public void applyVodStatsDelta(int viewDelta, int likeDelta, int reportDelta) {
        this.totalViews = Math.max(0, this.totalViews + viewDelta);
        this.totalLikes = Math.max(0, this.totalLikes + likeDelta);
        this.totalReports = Math.max(0, this.totalReports + reportDelta);
    }
}
