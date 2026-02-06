package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
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
  LocalDateTime cancelRequestedAt
) {
  public static OrderSummaryResponse from(Order order) {
    if (order == null) {
      return new OrderSummaryResponse(null, null, null, null, null, null, null);
    }
    return new OrderSummaryResponse(
      order.getId(),
      order.getOrderNumber(),
      order.getStatus(),
      order.getOrderAmount(),
      order.getCreatedAt(),
      order.getCancelReason(),
      order.getUpdatedAt()
    );
  }
}
