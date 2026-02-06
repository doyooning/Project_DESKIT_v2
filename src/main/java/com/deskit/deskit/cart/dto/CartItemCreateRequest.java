package com.deskit.deskit.cart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartItemCreateRequest {

  @JsonProperty("product_id")
  @NotNull
  @Positive
  private Long productId;

  @JsonProperty("quantity")
  @NotNull
  @Positive
  private Integer quantity;
}
