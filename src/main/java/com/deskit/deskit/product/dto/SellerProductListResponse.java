package com.deskit.deskit.product.dto;

import com.deskit.deskit.product.entity.Product;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record SellerProductListResponse(
  @JsonProperty("product_id")
  Long productId,

  @JsonProperty("product_name")
  String productName,

  @JsonProperty("price")
  Integer price,

  @JsonProperty("status")
  Product.Status status,

  @JsonProperty("stock_qty")
  Integer stockQty,

  @JsonProperty("created_at")
  LocalDateTime createdAt,

  @JsonProperty("thumbnail_url")
  String thumbnailUrl
) {
  public static SellerProductListResponse from(Product product, String thumbnailUrl) {
    if (product == null) {
      return new SellerProductListResponse(null, null, null, null, null, null, null);
    }
    Product.Status displayStatus =
      product.isLimitedSale() ? Product.Status.LIMITED_SALE : product.getStatus();
    return new SellerProductListResponse(
      product.getId(),
      product.getProductName(),
      product.getPrice(),
      displayStatus,
      product.getStockQty(),
      product.getCreatedAt(),
      thumbnailUrl
    );
  }
}
