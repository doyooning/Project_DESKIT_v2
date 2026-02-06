package com.deskit.deskit.order.dto;

import com.deskit.deskit.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
  @JsonProperty("status")
  @NotNull
  OrderStatus status
) {}
