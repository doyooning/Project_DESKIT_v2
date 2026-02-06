package com.deskit.deskit.livehost.entity;

import com.deskit.deskit.livehost.common.enums.BroadcastLayout;
import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.tag.entity.TagCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Table(name = "broadcast")
public class Broadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "broadcast_id")
    private Long broadcastId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_category_id", nullable = false)
    private TagCategory tagCategory;

    @Column(name = "broadcast_title", length = 30, nullable = false)
    private String broadcastTitle;

    @Column(name = "broadcast_notice", length = 50) // 화면정의서 50자 제한 반영
    private String broadcastNotice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BroadcastStatus status; // 기본값 제거 (DB Default 혹은 Builder 사용)

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "broadcast_thumb_url", nullable = false)
    private String broadcastThumbUrl;

    @Column(name = "broadcast_wait_url")
    private String broadcastWaitUrl;

    @Column(name = "stream_key", length = 100)
    private String streamKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_layout", nullable = false)
    private BroadcastLayout broadcastLayout;

    // 관리자 강제 종료 사유 (기존 변수명 유지)
    @Column(name = "broadcast_stopped_reason", length = 50)
    private String broadcastStoppedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "broadcast", cascade = CascadeType.ALL)
    @Builder.Default // Builder 패턴 사용 시 리스트 초기화 유지
    private List<BroadcastProduct> products = new ArrayList<>();

    @OneToMany(mappedBy = "broadcast", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Qcard> qcards = new ArrayList<>();

    // --- 비즈니스 로직 메서드 ---

    /**
     * [방송 정보 수정]
     * 주의: JPA 연관관계 업데이트를 위해 ID(Long)가 아닌 Entity 객체(TagCategory)를 받아야 합니다.
     */
    public void updateBroadcastInfo(TagCategory tagCategory, String title, String notice,
                                    LocalDateTime scheduledAt, String thumbUrl,
                                    String waitUrl, BroadcastLayout layout) {
        this.tagCategory = tagCategory;
        this.broadcastTitle = title;
        this.broadcastNotice = notice;
        this.scheduledAt = scheduledAt;
        this.broadcastThumbUrl = thumbUrl;
        this.broadcastWaitUrl = waitUrl;
        this.broadcastLayout = layout;
    }

    /**
     * [방송 중 정보 수정]
     * 방송 중에는 카테고리, 제목, 공지, 이미지 정도만 수정 가능 (시간 변경 불가)
     */
    public void updateLiveBroadcastInfo(TagCategory tagCategory, String title, String notice, String thumbUrl, String waitUrl) {
        this.tagCategory = tagCategory;
        this.broadcastTitle = title;
        this.broadcastNotice = notice;
        this.broadcastThumbUrl = thumbUrl;
        this.broadcastWaitUrl = waitUrl;
    }

    // VOD 상태 변경용 (AdminService에서 호출)
    public void changeStatus(BroadcastStatus status) {
        transitionTo(status, null);
    }

    // 방송 시작
    public void startBroadcast(String streamKey) {
        transitionTo(BroadcastStatus.ON_AIR, null);
        this.streamKey = streamKey;
        this.startedAt = LocalDateTime.now();
    }

    // 방송 종료 (정상 종료)
    public void endBroadcast() {
        transitionTo(BroadcastStatus.ENDED, null);
        this.endedAt = LocalDateTime.now();
    }

    // 예약 취소
    public void cancelBroadcast() {
        cancelBroadcast(null);
    }

    public void cancelBroadcast(String reason) {
        transitionTo(BroadcastStatus.CANCELED, reason);
    }

    // 삭제 (Soft Delete)
    public void deleteBroadcast() {
        this.status = BroadcastStatus.DELETED;
    }

    // [핵심] 관리자 강제 종료
    // 상태를 STOPPED로 변경하고, 사유를 기록하며, 종료 시간을 찍습니다.
    public void forceStopByAdmin(String reason) {
        transitionTo(BroadcastStatus.STOPPED, reason);
        this.endedAt = LocalDateTime.now();
    }

    public void readyBroadcast() {
        transitionTo(BroadcastStatus.READY, null);
    }

    public void markNoShow(String reason) {
        transitionTo(BroadcastStatus.CANCELED, reason);
    }

    public void transitionTo(BroadcastStatus targetStatus, String reason) {
        if (!isTransitionAllowed(this.status, targetStatus)) {
            throw new BusinessException(ErrorCode.BROADCAST_INVALID_TRANSITION);
        }
        this.status = targetStatus;
        if (targetStatus == BroadcastStatus.STOPPED || targetStatus == BroadcastStatus.CANCELED) {
            this.broadcastStoppedReason = reason;
        }
    }

    private boolean isTransitionAllowed(BroadcastStatus from, BroadcastStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case RESERVED -> to == BroadcastStatus.READY || to == BroadcastStatus.CANCELED;
            case CANCELED -> to == BroadcastStatus.RESERVED;
            case READY -> to == BroadcastStatus.ON_AIR || to == BroadcastStatus.CANCELED || to == BroadcastStatus.STOPPED;
            case ON_AIR -> to == BroadcastStatus.ENDED || to == BroadcastStatus.STOPPED;
            case ENDED -> to == BroadcastStatus.VOD || to == BroadcastStatus.STOPPED;
            case STOPPED -> to == BroadcastStatus.VOD;
            default -> false;
        };
    }
}
