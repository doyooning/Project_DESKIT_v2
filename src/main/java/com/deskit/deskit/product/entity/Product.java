package com.deskit.deskit.product.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

  public enum Status {
    // 작성 중 상태: 사용자에게 노출되지 않으며 주문 불가
    DRAFT,
    // 판매 준비 완료: 사용자 노출 가능, 주문 불가(판매 시작 전)
    READY,
    // 판매중: 사용자에게 노출되며 주문 가능
    ON_SALE,
    // 한정 판매: 재고 기반 표시 상태(직접 전이 금지, ON_SALE + 재고임박일 때만 계산)
    LIMITED_SALE,
    // 품절: 사용자에게 노출되며 주문 불가
    SOLD_OUT,
    // 일시중지: 사용자에게 노출되며 주문 불가
    PAUSED,
    // 숨김: 사용자에게 노출되지 않으며 주문 불가
    HIDDEN,
    // 삭제(논리삭제): 사용자에게 노출되지 않으며 주문 불가
    DELETED;

    public boolean canTransitionTo(Status next) {
      if (next == null) {
        return false;
      }
      return switch (this) {
        case DRAFT -> next == READY || next == DELETED;
        case READY -> next == ON_SALE || next == HIDDEN;
        case ON_SALE -> next == SOLD_OUT || next == PAUSED || next == HIDDEN;
        case SOLD_OUT -> next == ON_SALE || next == HIDDEN;
        case PAUSED -> next == ON_SALE || next == HIDDEN;
        case HIDDEN -> next == READY || next == DELETED;
        case DELETED, LIMITED_SALE -> false;
      };
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "product_id", nullable = false)
  private Long id;

  @Column(name = "seller_id", nullable = false)
  private Long sellerId;

  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  @Column(name = "short_desc", nullable = false, length = 250)
  private String shortDesc;

  @Lob
  @Column(name = "detail_html", nullable = false)
  private String detailHtml;

  @Column(name = "price", nullable = false)
  private Integer price;

  @Column(name = "cost_price", nullable = false)
  private Integer costPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status;

  @Column(name = "stock_qty", nullable = false)
  private Integer stockQty;

  @Column(name = "safety_stock", nullable = false)
  private Integer safetyStock;

  public Product(Long sellerId, String productName, String shortDesc, String detailHtml,
                 Integer price, Integer costPrice, Integer stockQty, Integer safetyStock) {
    this.sellerId = sellerId;
    this.productName = productName;
    this.shortDesc = shortDesc;
    this.detailHtml = detailHtml;
    this.price = price;
    this.costPrice = costPrice;
    this.status = Status.DRAFT;
    this.stockQty = stockQty;
    this.safetyStock = safetyStock;
  }

  public void decreaseStock(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be > 0");
    }
    if (this.stockQty == null) {
      this.stockQty = 0;
    }
    if (this.stockQty < quantity) {
      throw new IllegalStateException("insufficient stock");
    }
    this.stockQty -= quantity;
    if (this.stockQty == 0) {
      this.status = Status.SOLD_OUT;
    }
  }

  public void changeStatus(Status next) {
    if (next == null) {
      throw new IllegalStateException("status required");
    }
    if (!this.status.canTransitionTo(next)) {
      throw new IllegalStateException("invalid status transition");
    }
    this.status = next;
  }

  public void changeDetailHtml(String detailHtml) {
    if (detailHtml == null) {
      throw new IllegalArgumentException("detail_html required");
    }
    this.detailHtml = detailHtml;
  }

  public void updateProductName(String productName) {
    if (productName == null || productName.isBlank()) {
      throw new IllegalArgumentException("product_name required");
    }
    this.productName = productName;
  }

  public void updateShortDesc(String shortDesc) {
    if (shortDesc == null || shortDesc.isBlank()) {
      throw new IllegalArgumentException("short_desc required");
    }
    this.shortDesc = shortDesc;
  }

  public void updatePrice(Integer price) {
    if (price == null || price < 0) {
      throw new IllegalArgumentException("price must be >= 0");
    }
    this.price = price;
  }

  public void updateStockQty(Integer stockQty) {
    if (stockQty == null || stockQty < 0) {
      throw new IllegalArgumentException("stock_qty must be >= 0");
    }
    this.stockQty = stockQty;
  }

  public void changeCostPrice(Integer costPrice) {
    if (costPrice == null || costPrice < 0) {
      throw new IllegalArgumentException("cost_price must be >= 0");
    }
    this.costPrice = costPrice;
  }

  public void changePrice(Integer price) {
    if (price == null || price < 0) {
      throw new IllegalArgumentException("price must be >= 0");
    }
    this.price = price;
  }

  // LIMITED_SALE is derived from ON_SALE + low stock; it is not a persisted transition target.
  public boolean isLimitedSale() {
    if (this.status != Status.ON_SALE) {
      return false;
    }
    if (this.stockQty == null || this.safetyStock == null) {
      return false;
    }
    return this.stockQty <= this.safetyStock;
  }
}
