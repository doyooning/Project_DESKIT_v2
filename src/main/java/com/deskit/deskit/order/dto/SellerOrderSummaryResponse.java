package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record SellerOrderSummaryResponse(
  @JsonProperty("order_id")
  Long orderId,

  @JsonProperty("order_number")
  String orderNumber,

  @JsonProperty("status")
  OrderStatus status,

  @JsonProperty("order_amount")
  Integer orderAmount,

  @JsonProperty("created_at")
  LocalDateTime createdAt,

  @JsonProperty("paid_at")
  LocalDateTime paidAt,

  @JsonProperty("cancelled_at")
  LocalDateTime cancelledAt,

  @JsonProperty("refunded_at")
  LocalDateTime refundedAt,

  @JsonProperty("item_count")
  Integer itemCount,

  @JsonProperty("first_product_name")
  String firstProductName
) {
  public static SellerOrderSummaryResponse from(
    Order order,
    Integer itemCount,
    String firstProductName
  ) {
    if (order == null) {
      return new SellerOrderSummaryResponse(
        null, null, null, null, null, null, null, null, itemCount, firstProductName
      );
    }
    return new SellerOrderSummaryResponse(
      order.getId(),
      order.getOrderNumber(),
      order.getStatus(),
      order.getOrderAmount(),
      order.getCreatedAt(),
      order.getPaidAt(),
      order.getCancelledAt(),
      order.getRefundedAt(),
      itemCount,
      firstProductName
    );
  }
}
