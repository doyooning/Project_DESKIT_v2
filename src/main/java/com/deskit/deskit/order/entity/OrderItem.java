package com.deskit.deskit.order.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상품(라인 아이템) 엔티티
 *
 * - livecommerce_create_table.sql의 order_item 테이블 매핑
 * - 주문 시점의 상품 정보 스냅샷(product_name, unit_price 등)을 저장해서
 *   이후 상품 정보가 변경되어도 "당시 주문 내역"이 변하지 않도록 한다.
 * - BaseEntity를 상속하여 created_at/updated_at/deleted_at(소프트 삭제) 컬럼을 공통으로 사용
 */
@Entity
@Table(name = "order_item")
@Getter
// JPA 기본 생성자 요구사항 충족(외부에서 new로 생성하지 못하게 protected로 제한)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

  /**
   * 주문상품 PK
   * - order_item_id (AUTO_INCREMENT)
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "order_item_id", nullable = false)
  private Long id;

  /**
   * 소속 주문(부모)
   * - FK: order_item.order_id → `order`.order_id
   * - LAZY: 조회 시 주문상품 목록만 필요할 때 Order를 즉시 로딩하지 않도록
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  /**
   * 주문 시점 상품 ID (product.product_id)
   * - 연관관계 대신 값만 저장: 주문 데이터는 스냅샷 성격이 강하고, 결합도를 낮추기 위함
   */
  @Column(name = "product_id", nullable = false)
  private Long productId;

  /**
   * 판매자 ID (seller.seller_id)
   * - 주문 단위 정산/조회 시 seller_id가 빠르게 필요해서 스냅샷으로 보관
   */
  @Column(name = "seller_id", nullable = false)
  private Long sellerId;

  /**
   * 주문 시점 상품명 스냅샷
   * - 상품명이 변경되어도 주문 내역은 당시 기준으로 유지
   */
  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  /**
   * 주문 시점 단가 스냅샷
   * - unit_price
   */
  @Column(name = "unit_price", nullable = false)
  private Integer unitPrice;

  /**
   * 수량
   * - quantity
   */
  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  /**
   * 라인 금액(단가 * 수량)
   * - subtotal_price
   * - 보통 unitPrice * quantity로 계산되지만, 쿠폰/프로모션 등을 라인에 반영할 경우
   *   저장값을 스냅샷으로 유지하는 편이 안전할 수 있음
   */
  @Column(name = "subtotal_price", nullable = false)
  private Integer subtotalPrice;

  /**
   * 주문상품 생성 팩토리 메서드
   *
   * - 주문 생성 시 필요한 스냅샷 값을 한 번에 세팅하기 위한 최소 형태
   * - (추후) subtotalPrice를 내부에서 계산하도록 바꾸면 불일치 위험을 더 줄일 수 있음
   */
  public static OrderItem create(
          Order order,
          Long productId,
          Long sellerId,
          String productName,
          Integer unitPrice,
          Integer quantity,
          Integer subtotalPrice
  ) {
    OrderItem item = new OrderItem();
    item.order = order;
    item.productId = productId;
    item.sellerId = sellerId;
    item.productName = productName;
    item.unitPrice = unitPrice;
    item.quantity = quantity;
    item.subtotalPrice = subtotalPrice;
    return item;
  }
}