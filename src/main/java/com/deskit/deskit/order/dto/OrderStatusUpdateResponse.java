package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderStatusUpdateResponse(
  @JsonProperty("order_id")
  Long orderId,

  @JsonProperty("status")
  OrderStatus status
) {}
