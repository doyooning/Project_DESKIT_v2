package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record SellerOrderDetailResponse(
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

  @JsonProperty("items")
  List<OrderItemResponse> items
) {
  public static SellerOrderDetailResponse from(Order order, List<OrderItemResponse> items) {
    if (order == null) {
      return new SellerOrderDetailResponse(null, null, null, null, null, null, null, null, items);
    }
    return new SellerOrderDetailResponse(
      order.getId(),
      order.getOrderNumber(),
      order.getStatus(),
      order.getOrderAmount(),
      order.getCreatedAt(),
      order.getPaidAt(),
      order.getCancelledAt(),
      order.getRefundedAt(),
      items
    );
  }
}
