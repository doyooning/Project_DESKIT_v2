package com.deskit.deskit.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
  @JsonProperty("product_id")
  @NotNull
  Long productId,

  @JsonProperty("quantity")
  @NotNull
  @Positive
  Integer quantity
) {}
