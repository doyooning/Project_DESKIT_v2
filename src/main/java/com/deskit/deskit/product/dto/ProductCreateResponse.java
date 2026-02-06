package com.deskit.deskit.product.dto;

import com.deskit.deskit.product.entity.Product;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ProductCreateResponse(
  @JsonProperty("product_id")
  Long productId,

  @JsonProperty("status")
  Product.Status status,

  @JsonProperty("created_at")
  LocalDateTime createdAt
) {
  public static ProductCreateResponse from(Product product) {
    if (product == null) {
      return new ProductCreateResponse(null, null, null);
    }
    return new ProductCreateResponse(
      product.getId(),
      product.getStatus(),
      product.getCreatedAt()
    );
  }
}
