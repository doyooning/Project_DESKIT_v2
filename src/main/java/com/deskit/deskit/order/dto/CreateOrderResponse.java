package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateOrderResponse(
  @JsonProperty("order_id")
  Long orderId,

  @JsonProperty("order_number")
  String orderNumber,

  @JsonProperty("status")
  OrderStatus status,

  @JsonProperty("order_amount")
  Integer orderAmount
) {}
