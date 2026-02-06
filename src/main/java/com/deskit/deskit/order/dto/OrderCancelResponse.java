package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderCancelResponse(
  @JsonProperty("order_id")
  Long orderId,

  @JsonProperty("status")
  OrderStatus status
) {}
