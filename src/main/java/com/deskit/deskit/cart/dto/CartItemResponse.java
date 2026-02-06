package com.deskit.deskit.cart.dto;

import com.deskit.deskit.cart.entity.CartItem;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CartItem 응답 DTO
 * - API 응답에서 snake_case로 내려주기 위해 @JsonProperty 사용
 * - record라서 불변(immutable) + 생성자/게터 자동 생성
 */
public record CartItemResponse(
        @JsonProperty("cart_item_id") Long cartItemId,     // cart_item.cart_item_id
        @JsonProperty("product_id") Long productId,        // cart_item.product_id
        @JsonProperty("quantity") Integer quantity,        // cart_item.quantity
        @JsonProperty("price_snapshot") Integer priceSnapshot // cart_item.price_snapshot
) {

  /**
   * Entity -> DTO 변환 팩토리 메서드
   * - item이 null이면 NPE 방지를 위해 null 필드로 구성된 응답을 반환
   *   (보통은 null이 들어오지 않게 서비스 레벨에서 보장하는 편이 더 깔끔함)
   * - 연관관계(Product)가 null일 수 있으니 productId도 안전하게 꺼냄
   */
  public static CartItemResponse from(CartItem item) {
    if (item == null) {
      return new CartItemResponse(null, null, null, null);
    }

    // CartItem -> Product는 ManyToOne이라 LAZY일 수 있음
    // 영속성 컨텍스트 밖에서 호출하면 LazyInitializationException 날 수 있으니
    // 조회 쿼리에서 fetch join 하거나 DTO projection을 고려하면 더 안전함.
    Long productId = item.getProduct() != null ? item.getProduct().getId() : null;

    return new CartItemResponse(
            item.getId(),            // cart_item_id
            productId,               // product_id
            item.getQuantity(),      // quantity
            item.getPriceSnapshot()  // price_snapshot
    );
  }
}