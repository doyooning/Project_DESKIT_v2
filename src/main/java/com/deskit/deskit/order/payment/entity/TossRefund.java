package com.deskit.deskit.order.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "toss_refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossRefund {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "refund_id", nullable = false)
  private Long id;

  @Column(name = "refund_key", nullable = false, length = 100)
  private String refundKey;

  @Column(name = "refund_amount", nullable = false)
  private Long refundAmount;

  @Column(name = "refund_reason", length = 255)
  private String refundReason;

  @Column(name = "refund_status", nullable = false, length = 255)
  private String refundStatus;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "toss_payment_key", nullable = false, unique = true, length = 64)
  private String tossPaymentKey;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public static TossRefund create(
    String refundKey,
    Long refundAmount,
    String refundReason,
    String refundStatus,
    LocalDateTime requestedAt,
    LocalDateTime approvedAt,
    Long paymentId,
    String tossPaymentKey
  ) {
    TossRefund refund = new TossRefund();
    refund.refundKey = refundKey;
    refund.refundAmount = refundAmount;
    refund.refundReason = refundReason;
    refund.refundStatus = refundStatus;
    refund.requestedAt = requestedAt;
    refund.approvedAt = approvedAt;
    refund.paymentId = paymentId;
    refund.tossPaymentKey = tossPaymentKey;
    return refund;
  }
}
