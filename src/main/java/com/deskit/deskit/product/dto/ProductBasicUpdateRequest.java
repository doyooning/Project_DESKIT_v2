package com.deskit.deskit.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import java.util.List;

public record ProductBasicUpdateRequest(
  @JsonProperty("product_name")
  String productName,

  @JsonProperty("short_desc")
  String shortDesc,

  @JsonProperty("price")
  @Min(0)
  Integer price,

  @JsonProperty("stock_qty")
  @Min(0)
  Integer stockQty,

  @JsonProperty("detail_html")
  String detailHtml,

  @JsonProperty("image_urls")
  List<String> imageUrls,

  @JsonProperty("image_keys")
  List<String> imageKeys
) {}
