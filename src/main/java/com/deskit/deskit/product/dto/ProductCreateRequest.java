package com.deskit.deskit.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductCreateRequest(
  @JsonProperty("product_name")
  @NotBlank
  String productName,

  @JsonProperty("short_desc")
  @NotBlank
  String shortDesc,

  @JsonProperty("detail_html")
  String detailHtml,

  @JsonProperty("price")
  @NotNull
  @Min(0)
  Integer price,

  @JsonProperty("stock_qty")
  @NotNull
  @Min(0)
  Integer stockQty,

  @JsonProperty("cost_price")
  @Min(0)
  Integer costPrice
) {}
