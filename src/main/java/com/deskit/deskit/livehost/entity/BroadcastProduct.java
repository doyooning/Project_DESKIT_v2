package com.deskit.deskit.livehost.entity;

import com.deskit.deskit.livehost.common.enums.BroadcastProductStatus;
import com.deskit.deskit.livehost.common.utils.BooleanToYNConverter;
import com.deskit.deskit.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Table(name = "broadcast_product")
public class BroadcastProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bp_id")
    private Long bpId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private Broadcast broadcast;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "bp_price")
    private Integer bpPrice; // 라이브 특가

    @Column(name = "bp_quantity", nullable = false)
    private int bpQuantity;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_pinned", nullable = false, columnDefinition = "char(1)")
    private boolean isPinned; // DB엔 'Y'/'N', 자바엔 true/false

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BroadcastProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean markSoldOutIfNeeded(Integer remainingQuantity) {
        int remaining = remainingQuantity == null ? 0 : remainingQuantity;
        if (remaining > 0) {
            return false;
        }
        if (status == BroadcastProductStatus.SOLDOUT) {
            return false;
        }
        status = BroadcastProductStatus.SOLDOUT;
        return true;
    }
}
