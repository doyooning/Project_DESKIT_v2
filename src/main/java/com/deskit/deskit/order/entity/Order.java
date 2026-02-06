package com.deskit.deskit.order.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import com.deskit.deskit.order.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문(헤더) 엔티티
 *
 * - livecommerce_create_table.sql의 `order` 테이블 매핑
 * - BaseEntity를 상속하여 created_at/updated_at/deleted_at(소프트 삭제) 컬럼을 공통으로 사용
 * - 결제/취소 시각(paid_at/cancelled_at)은 주문 특화 필드이므로 Order에 별도 필드로 보관
 */
@Entity
@Table(name = "`order`")
@Getter
// JPA 기본 생성자 요구사항 충족(외부에서 new로 생성하지 못하게 protected로 제한)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

  /**
   * 주문 PK
   * - order_id (AUTO_INCREMENT)
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "order_id", nullable = false)
  private Long id;

  /**
   * 주문자 식별자
   * - member 테이블의 member_id를 값으로만 저장(연관관계 대신 primitive id)
   * - 주문은 "스냅샷성 데이터"라서, 회원 엔티티 변경/삭제와 강결합을 피하려는 의도
   */
  @Column(name = "member_id", nullable = false)
  private Long memberId;

  /**
   * Shipping address detail snapshot.
   * - addr_detail (nullable)
   */
  @Column(name = "addr_detail", length = 255)
  private String addrDetail;

  /**
   * 주문번호(외부 노출용 식별자)
   * - order_number (VARCHAR(50))
   * - 보통 "ORD-YYYYMMDD-XXXX" 같은 규칙으로 생성
   */
  @Column(name = "order_number", nullable = false, length = 50)
  private String orderNumber;

  /**
   * 상품 금액 합계(할인/배송비 적용 전 기준으로 사용)
   * - total_product_amount
   */
  @Column(name = "total_product_amount", nullable = false)
  private Integer totalProductAmount;

  /**
   * 배송비
   * - shipping_fee (DEFAULT 0)
   */
  @Column(name = "shipping_fee", nullable = false)
  private Integer shippingFee;

  /**
   * 할인 금액(쿠폰/프로모션 등)
   * - discount_fee (DEFAULT 0)
   */
  @Column(name = "discount_fee", nullable = false)
  private Integer discountFee;

  /**
   * 최종 결제 금액
   * - order_amount
   * - 일반적으로 total_product_amount + shipping_fee - discount_fee
   */
  @Column(name = "order_amount", nullable = false)
  private Integer orderAmount;

  /**
   * 주문 상태
   * - SQL ENUM('CREATED','PAID','CANCEL_REQUESTED','CANCELLED','COMPLETED','REFUND_REQUESTED','REFUND_REJECTED','REFUNDED')
   * - EnumType.STRING으로 저장하여 값이 명확하고, enum 순서 변경에 안전
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OrderStatus status;

  /**
   * 취소 사유
   * - cancel_reason (nullable)
   */
  @Column(name = "cancel_reason", length = 500)
  private String cancelReason;

  /**
   * 결제 완료 시각
   * - paid_at (nullable)
   */
  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  /**
   * 취소 시각
   * - cancelled_at (nullable)
   */
  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  /**
   * 환불 완료 시각
   * - refunded_at (nullable)
   */
  @Column(name = "refunded_at")
  private LocalDateTime refundedAt;

  /**
   * 주문 생성 팩토리 메서드
   *
   * - 엔티티 생성 시 필요한 핵심 값들을 한 곳에서 세팅하기 위해 제공
   * - 현재는 "값만 세팅"하는 최소 형태(검증/계산 로직은 이후 서비스 레이어에서 확장 가능)
   */
  public static Order create(
          Long memberId,
          String addrDetail,
          String orderNumber,
          Integer totalProductAmount,
          Integer shippingFee,
          Integer discountFee,
          Integer orderAmount,
          OrderStatus status
  ) {
    Order order = new Order();
    order.memberId = memberId;
    order.addrDetail = addrDetail;
    order.orderNumber = orderNumber;
    order.totalProductAmount = totalProductAmount;
    order.shippingFee = shippingFee;
    order.discountFee = discountFee;
    order.orderAmount = orderAmount;
    order.status = status;
    return order;
  }

  public void markPaid() {
    if (this.status == OrderStatus.PAID) {
      return;
    }
    if (this.status != OrderStatus.CREATED) {
      throw new IllegalStateException("invalid status for paid");
    }
    this.status = OrderStatus.PAID;
    if (this.paidAt == null) {
      this.paidAt = LocalDateTime.now();
    }
  }

  public void requestCancel(String reason) {
    if (this.status == OrderStatus.CREATED) {
      if (this.cancelReason == null && reason != null) {
        this.cancelReason = reason;
      }
      this.status = OrderStatus.CANCEL_REQUESTED;
      return;
    }
    if (this.status == OrderStatus.PAID) {
      if (this.cancelReason == null && reason != null) {
        this.cancelReason = reason;
      }
      this.status = OrderStatus.REFUND_REQUESTED;
      return;
    }
    throw new IllegalStateException("invalid status for cancel request");
  }

  public void approveCancel() {
    if (this.status != OrderStatus.CANCEL_REQUESTED) {
      throw new IllegalStateException("invalid status for cancel approval");
    }
    this.status = OrderStatus.CANCELLED;
    if (this.cancelledAt == null) {
      this.cancelledAt = LocalDateTime.now();
    }
  }

  public void approveRefund() {
    if (this.status != OrderStatus.REFUND_REQUESTED) {
      throw new IllegalStateException("invalid status for refund approval");
    }
    this.status = OrderStatus.REFUNDED;
    if (this.refundedAt == null) {
      this.refundedAt = LocalDateTime.now();
    }
  }

  public void rejectRefund() {
    if (this.status != OrderStatus.REFUND_REQUESTED) {
      throw new IllegalStateException("invalid status for refund rejection");
    }
    this.status = OrderStatus.REFUND_REJECTED;
  }

}
