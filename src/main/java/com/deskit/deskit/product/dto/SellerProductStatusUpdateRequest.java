package com.deskit.deskit.product.dto;

import com.deskit.deskit.product.entity.Product;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record SellerProductStatusUpdateRequest(
  @NotNull
  @JsonProperty("status")
  Product.Status status
) {
}
