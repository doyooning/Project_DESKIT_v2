package com.deskit.deskit.livehost.entity;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.livehost.common.enums.ActorType;
import com.deskit.deskit.livehost.common.enums.SanctionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "sanction")
public class Sanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_id")
    private Long sanctionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private Broadcast broadcast;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "seller_id")
    private Long sellerId; // 판매자 ID

    @Column(name = "admin_id")
    private Long adminId; // 관리자 ID

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SanctionType status;

    @Column(name = "sanction_reason", length = 50)
    private String sanctionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}

