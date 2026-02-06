package com.deskit.deskit.order.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "toss_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossPayment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payment_id", nullable = false)
  private Long id;

  @Column(name = "toss_payment_key", nullable = false, unique = true, length = 64)
  private String tossPaymentKey;

  @Column(name = "toss_order_id", nullable = false, length = 255)
  private String tossOrderId;

  @Column(name = "toss_payment_method", nullable = false)
  private String tossPaymentMethod;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "request_date", nullable = false)
  private LocalDateTime requestDate;

  @Column(name = "approved_date")
  private LocalDateTime approvedDate;

  @Column(name = "total_amount", nullable = false)
  private Long totalAmount;

  @Column(name = "order_id", nullable = false, length = 36)
  private String orderId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public static TossPayment create(
    String tossPaymentKey,
    String tossOrderId,
    String tossPaymentMethod,
    String status,
    LocalDateTime requestDate,
    LocalDateTime approvedDate,
    Long totalAmount,
    String orderId
  ) {
    TossPayment payment = new TossPayment();
    payment.tossPaymentKey = tossPaymentKey;
    payment.tossOrderId = tossOrderId;
    payment.tossPaymentMethod = tossPaymentMethod;
    payment.status = status;
    payment.requestDate = requestDate;
    payment.approvedDate = approvedDate;
    payment.totalAmount = totalAmount;
    payment.orderId = orderId;
    return payment;
  }

  public void updateStatus(String status) {
    if (status == null || status.isBlank()) {
      return;
    }
    this.status = status;
  }
}
