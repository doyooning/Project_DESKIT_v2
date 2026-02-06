package com.deskit.deskit.livehost.entity;

import com.deskit.deskit.livehost.common.enums.VodStatus;
import com.deskit.deskit.livehost.common.utils.BooleanToYNConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "vod")
public class Vod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vod_id")
    private Long vodId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private Broadcast broadcast;

    @Column(name = "vod_url")
    private String vodUrl;

    @Column(name = "vod_size", nullable = false)
    private Long vodSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VodStatus status;

    @Column(name = "vod_report_count", nullable = false)
    private int vodReportCount;

    @Column(name = "vod_duration")
    private Integer vodDuration;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "vod_admin_lock", nullable = false, columnDefinition = "char(1)")
    private boolean vodAdminLock;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void changeStatus(VodStatus newStatus) {
        this.status = newStatus;
    }

    public void setAdminLock(boolean lock) {
        this.vodAdminLock = lock;
    }

    public void markDeleted() {
        this.status = VodStatus.DELETED;
        this.vodUrl = null;
        this.vodSize = 0L;
    }

    public void applyReportDelta(int delta) {
        this.vodReportCount = Math.max(0, this.vodReportCount + delta);
    }
}
