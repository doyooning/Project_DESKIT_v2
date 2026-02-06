package com.deskit.deskit.product.dto;

import com.deskit.deskit.product.entity.Product;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SellerProductStatusUpdateResponse(
  @JsonProperty("product_id")
  Long productId,

  @JsonProperty("status")
  Product.Status status
) {
  public static SellerProductStatusUpdateResponse from(Product product) {
    if (product == null) {
      return new SellerProductStatusUpdateResponse(null, null);
    }
    return new SellerProductStatusUpdateResponse(product.getId(), product.getStatus());
  }
}
