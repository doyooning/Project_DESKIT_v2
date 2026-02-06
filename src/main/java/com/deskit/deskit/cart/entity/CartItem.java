package com.deskit.deskit.cart.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import com.deskit.deskit.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity // JPA 엔티티로 등록 (cart_item 테이블과 매핑)
@Table(
        name = "cart_item",
        uniqueConstraints = {
                // 한 장바구니(cart_id) 안에서 같은 상품(product_id)은 1개 row만 존재하도록 강제
                // => "같은 상품 담기"는 row를 추가하는게 아니라 quantity를 증가시키는 방식이 자연스러움
                @UniqueConstraint(name = "uk_cart_item", columnNames = {"cart_id", "product_id"})
        }
)
@Getter // Lombok: getter 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// JPA 기본 생성자 요구사항 충족 + 외부에서 무분별한 생성 방지
public class CartItem extends BaseEntity {
  // BaseEntity: createdAt/updatedAt/deletedAt 같은 공통 컬럼 상속(프로젝트 기준)

  @Id // PK
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // MySQL AUTO_INCREMENT 대응
  @Column(name = "cart_item_id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  // cart_item N개가 cart 1개에 속함 (장바구니 1 : 아이템 N)
  @JoinColumn(name = "cart_id", nullable = false)
  // cart_item.cart_id FK
  private Cart cart;

  @ManyToOne(fetch = FetchType.LAZY)
  // cart_item N개가 product 1개를 참조 (상품 1 : 담긴아이템 N)
  @JoinColumn(name = "product_id", nullable = false)
  // cart_item.product_id FK
  private Product product;

  @Column(name = "quantity", nullable = false)
  // 담은 수량 (NOT NULL)
  private Integer quantity;

  @Column(name = "price_snapshot", nullable = false)
  // 담는 시점의 가격 스냅샷
  // => 상품 가격이 변해도 "담았을 때 가격"을 표시/검증할 수 있음
  private Integer priceSnapshot;

  public CartItem(Cart cart, Product product, Integer quantity, Integer priceSnapshot) {
    // 생성 시 장바구니/상품/수량/가격스냅샷은 필수라는 도메인 규칙을 강제
    this.cart = cart;
    this.product = product;
    this.quantity = quantity;
    this.priceSnapshot = priceSnapshot;
  }

  public void changeQuantity(Integer quantity) {
    // 수량 변경용 도메인 메서드
    // - null 방지
    // - 1 미만(0 이하) 방지: 장바구니 아이템은 "0개" 상태가 의미 없으니 삭제 API로 처리하는 게 자연스러움
    if (quantity == null) {
      throw new IllegalArgumentException("quantity must not be null");
    }
    if (quantity < 1) {
      throw new IllegalArgumentException("quantity must be at least 1");
    }
    this.quantity = quantity;
  }

  public void updatePriceSnapshot(Integer priceSnapshot) {
    if (priceSnapshot == null || priceSnapshot < 0) {
      throw new IllegalArgumentException("price_snapshot must be >= 0");
    }
    this.priceSnapshot = priceSnapshot;
  }

  public void softDelete() {
    setDeletedAt(LocalDateTime.now());
  }
}
