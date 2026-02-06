package com.deskit.deskit.home.dto;

import com.deskit.deskit.product.repository.ProductRepository.PopularProductRow;
import com.fasterxml.jackson.annotation.JsonProperty;

public class HomePopularProductResponse {

  @JsonProperty("product_id")
  private final Long productId;

  @JsonProperty("name")
  private final String name;

  @JsonProperty("price")
  private final Integer price;

  @JsonProperty("sold_qty")
  private final Long soldQty;

  @JsonProperty("thumbnail_url")
  private final String thumbnailUrl;

  public HomePopularProductResponse(Long productId, String name, Integer price,
                                    Long soldQty, String thumbnailUrl) {
    this.productId = productId;
    this.name = name;
    this.price = price;
    this.soldQty = soldQty == null ? 0L : soldQty;
    this.thumbnailUrl = thumbnailUrl;
  }

  public static HomePopularProductResponse from(PopularProductRow row) {
    return new HomePopularProductResponse(
        row.getProductId(),
        row.getProductName(),
        row.getPrice(),
        row.getSoldQty(),
        row.getThumbnailUrl()
    );
  }
}
