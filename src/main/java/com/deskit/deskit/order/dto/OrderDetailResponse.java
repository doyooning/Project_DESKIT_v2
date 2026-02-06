package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
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

  @JsonProperty("cancel_reason")
  String cancelReason,

  @JsonProperty("cancel_requested_at")
  LocalDateTime cancelRequestedAt,

  @JsonProperty("items")
  List<OrderItemResponse> items
) {
  public static OrderDetailResponse from(Order order, List<OrderItemResponse> items) {
    if (order == null) {
      return new OrderDetailResponse(null, null, null, null, null, null, null, items);
    }
    return new OrderDetailResponse(
      order.getId(),
      order.getOrderNumber(),
      order.getStatus(),
      order.getOrderAmount(),
      order.getCreatedAt(),
      order.getCancelReason(),
      order.getUpdatedAt(),
      items
    );
  }
}
