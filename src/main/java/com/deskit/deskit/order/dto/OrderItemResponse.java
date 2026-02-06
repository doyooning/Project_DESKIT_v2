package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.entity.OrderItem;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItemResponse(
  @JsonProperty("order_item_id")
  Long orderItemId,

  @JsonProperty("product_id")
  Long productId,

  @JsonProperty("product_name")
  String productName,

  @JsonProperty("quantity")
  Integer quantity,

  @JsonProperty("unit_price")
  Integer unitPrice,

  @JsonProperty("subtotal_price")
  Integer subtotalPrice
) {
  public static OrderItemResponse from(OrderItem item) {
    if (item == null) {
      return new OrderItemResponse(null, null, null, null, null, null);
    }
    return new OrderItemResponse(
      item.getId(),
      item.getProductId(),
      item.getProductName(),
      item.getQuantity(),
      item.getUnitPrice(),
      item.getSubtotalPrice()
    );
  }
}
