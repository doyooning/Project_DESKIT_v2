package com.deskit.deskit.product.dto;

import com.deskit.deskit.product.entity.ProductImage;
import com.deskit.deskit.product.entity.ProductImage.ImageType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductImageResponse(
  @JsonProperty("product_image_id")
  Long productImageId,

  @JsonProperty("product_image_url")
  String productImageUrl,

  @JsonProperty("image_type")
  ImageType imageType,

  @JsonProperty("slot_index")
  Integer slotIndex
) {
  public static ProductImageResponse from(ProductImage image) {
    if (image == null) {
      return new ProductImageResponse(null, null, null, null);
    }
    return new ProductImageResponse(
      image.getId(),
      image.getProductImageUrl(),
      image.getImageType(),
      image.getSlotIndex()
    );
  }
}
