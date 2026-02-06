package com.deskit.deskit.setup.dto;

import com.deskit.deskit.setup.entity.Setup;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.List;

/**
 * Setup 조회 API 응답 DTO
 * - 프론트(mock 데이터)와 필드명을 맞추기 위해 snake_case(JSON)로 내려줌 (@JsonProperty)
 * - tags: 카테고리별 리스트(space/tone/situation/mood)
 * - tagsFlat: tags를 카테고리 순서대로 합친 단일 리스트(UI 편의용)
 */
public class SetupResponse {

  @JsonProperty("setup_id") // JSON 키를 setup_id로 고정
  private final Long setupId;

  @JsonProperty("seller_id")
  private final Long sellerId;

  @JsonProperty("name")
  private final String name;

  @JsonProperty("short_desc")
  private final String shortDesc;

  @JsonProperty("tip_text")
  private final String tipText;

  @JsonProperty("setup_image_url")
  private final String setupImageUrl;

  @JsonProperty("product_ids")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private final List<Long> productIds;

  @JsonProperty("tags")
  private final SetupTags tags;

  @JsonProperty("tagsFlat")
  private final List<String> tagsFlat;

  /**
   * 생성자에서 null-safe 처리:
   * - tags/tagsFlat이 null로 들어오면 빈 값으로 치환하여 응답 안정성을 높임
   */
  public SetupResponse(Long setupId, Long sellerId, String name, String shortDesc,
                       String tipText, String setupImageUrl, SetupTags tags,
                       List<String> tagsFlat) {
    this(setupId, sellerId, name, shortDesc, tipText, setupImageUrl,
            Collections.emptyList(), tags, tagsFlat);
  }

  public SetupResponse(Long setupId, Long sellerId, String name, String shortDesc,
                       String tipText, String setupImageUrl, List<Long> productIds,
                       SetupTags tags, List<String> tagsFlat) {
    this.setupId = setupId;
    this.sellerId = sellerId;
    this.name = name;
    this.shortDesc = shortDesc;
    this.tipText = tipText;
    this.setupImageUrl = setupImageUrl;
    this.productIds = productIds == null ? Collections.emptyList() : productIds;
    this.tags = tags == null ? SetupTags.empty() : tags;
    this.tagsFlat = tagsFlat == null ? Collections.emptyList() : tagsFlat;
  }

  /**
   * 엔티티 -> DTO 변환 팩토리 메서드
   * - 엔티티 필드명(setupName 등)과 응답 필드명(name 등)을 여기서 매핑
   */
  public static SetupResponse from(Setup setup, SetupTags tags, List<String> tagsFlat) {
    return from(setup, tags, tagsFlat, setup.getSetupImageUrl());
  }

  public static SetupResponse from(Setup setup, SetupTags tags, List<String> tagsFlat,
                                   List<Long> productIds) {
    return from(setup, tags, tagsFlat, productIds, setup.getSetupImageUrl());
  }

  public static SetupResponse from(Setup setup, SetupTags tags, List<String> tagsFlat,
                                   String setupImageUrl) {
    return new SetupResponse(
            setup.getId(),
            setup.getSellerId(),
            setup.getSetupName(),      // 엔티티 setupName -> 응답 name
            setup.getShortDesc(),
            setup.getTipText(),
            setupImageUrl,
            tags,
            tagsFlat
    );
  }

  public static SetupResponse from(Setup setup, SetupTags tags, List<String> tagsFlat,
                                   List<Long> productIds, String setupImageUrl) {
    return new SetupResponse(
            setup.getId(),
            setup.getSellerId(),
            setup.getSetupName(),      // 엔티티 setupName -> 응답 name
            setup.getShortDesc(),
            setup.getTipText(),
            setupImageUrl,
            productIds,
            tags,
            tagsFlat
    );
  }

  /**
   * tags 객체의 JSON 필드 출력 순서를 고정 (보기 좋게 + 프론트 기대 순서)
   */
  @JsonPropertyOrder({"space", "tone", "situation", "mood"})
  public static class SetupTags {

    @JsonProperty("space")
    private final List<String> space;

    @JsonProperty("tone")
    private final List<String> tone;

    @JsonProperty("situation")
    private final List<String> situation;

    @JsonProperty("mood")
    private final List<String> mood;

    /**
     * 카테고리별 태그 리스트를 담는 객체
     * - null로 들어오면 빈 리스트로 치환 (null-safe)
     */
    public SetupTags(List<String> space, List<String> tone,
                     List<String> situation, List<String> mood) {
      this.space = space == null ? Collections.emptyList() : space;
      this.tone = tone == null ? Collections.emptyList() : tone;
      this.situation = situation == null ? Collections.emptyList() : situation;
      this.mood = mood == null ? Collections.emptyList() : mood;
    }

    /** 빈 tags 기본값 제공 */
    public static SetupTags empty() {
      return new SetupTags(Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), Collections.emptyList());
    }

    // Jackson이 직렬화 시 getter를 통해 값을 읽기 때문에 getter 제공
    public List<String> getSpace() {
      return space;
    }

    public List<String> getTone() {
      return tone;
    }

    public List<String> getSituation() {
      return situation;
    }

    public List<String> getMood() {
      return mood;
    }
  }

  public List<Long> getProductIds() {
    return productIds;
  }
}
