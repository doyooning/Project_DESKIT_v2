package com.deskit.deskit.home.dto;

import com.deskit.deskit.setup.repository.SetupRepository.PopularSetupRow;
import com.fasterxml.jackson.annotation.JsonProperty;

public class HomePopularSetupResponse {

  @JsonProperty("setup_id")
  private final Long setupId;

  @JsonProperty("name")
  private final String name;

  @JsonProperty("short_desc")
  private final String shortDesc;

  @JsonProperty("sold_qty")
  private final Long soldQty;

  @JsonProperty("image_url")
  private final String imageUrl;

  public HomePopularSetupResponse(Long setupId, String name, String shortDesc,
                                  Long soldQty, String imageUrl) {
    this.setupId = setupId;
    this.name = name;
    this.shortDesc = shortDesc;
    this.soldQty = soldQty == null ? 0L : soldQty;
    this.imageUrl = imageUrl;
  }

  public static HomePopularSetupResponse from(PopularSetupRow row) {
    return new HomePopularSetupResponse(
        row.getSetupId(),
        row.getSetupName(),
        row.getShortDesc(),
        row.getSoldQty(),
        row.getImageUrl()
    );
  }
}
