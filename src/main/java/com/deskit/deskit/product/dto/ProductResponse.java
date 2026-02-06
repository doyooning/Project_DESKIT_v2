package com.deskit.deskit.product.dto;

import com.deskit.deskit.product.entity.Product;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.List;

/**
 * Product 조회 API 응답 DTO
 * - 프론트(mock 데이터)와 호환되도록 snake_case(JSON) 키를 사용 (@JsonProperty)
 * - tags: 카테고리별(space/tone/situation/mood) 태그 리스트
 * - tagsFlat: tags를 카테고리 순서대로 합친 1차원 리스트 (UI/필터링 편의)
 */
public class ProductResponse {

  @JsonProperty("product_id") // JSON 키를 product_id로 고정
  private final Long productId;

  @JsonProperty("seller_id")
  private final Long sellerId;

  @JsonProperty("name")
  private final String name;

  @JsonProperty("short_desc")
  private final String shortDesc;

  @JsonProperty("detail_html")
  private final String detailHtml;

  @JsonProperty("price")
  private final Integer price;

  @JsonProperty("cost_price")
  private final Integer costPrice;

  @JsonProperty("status")
  private final Product.Status status;

  @JsonProperty("stock_qty")
  private final Integer stockQty;

  @JsonProperty("safety_stock")
  private final Integer safetyStock;

  @JsonProperty("tags")
  private final ProductTags tags;

  @JsonProperty("tagsFlat")
  private final List<String> tagsFlat;

  @JsonProperty("thumbnail_url")
  private final String thumbnailUrl;

  @JsonProperty("product_images")
  private final List<ProductImageResponse> productImages;

  /**
   * 생성자에서 null-safe 처리:
   * - tags/tagsFlat이 null로 들어오면 빈 값으로 치환해서 응답 안정성 확보
   */
  public ProductResponse(Long productId, Long sellerId, String name, String shortDesc,
                         String detailHtml, Integer price, Integer costPrice,
                         Product.Status status, Integer stockQty, Integer safetyStock,
                         ProductTags tags, List<String> tagsFlat) {
    this(productId, sellerId, name, shortDesc, detailHtml, price, costPrice, status, stockQty, safetyStock, tags, tagsFlat, null, null);
  }

  public ProductResponse(Long productId, Long sellerId, String name, String shortDesc,
                         String detailHtml, Integer price, Integer costPrice,
                         Product.Status status, Integer stockQty, Integer safetyStock,
                         ProductTags tags, List<String> tagsFlat, String thumbnailUrl,
                         List<ProductImageResponse> productImages) {
    this.productId = productId;
    this.sellerId = sellerId;
    this.name = name;
    this.shortDesc = shortDesc;
    this.detailHtml = detailHtml;
    this.price = price;
    this.costPrice = costPrice;
    this.status = status;
    this.stockQty = stockQty;
    this.safetyStock = safetyStock;
    this.tags = tags == null ? ProductTags.empty() : tags;
    this.tagsFlat = tagsFlat == null ? Collections.emptyList() : tagsFlat;
    this.thumbnailUrl = thumbnailUrl;
    this.productImages = productImages == null ? Collections.emptyList() : productImages;
  }

  /**
   * 엔티티 -> DTO 변환 팩토리 메서드
   * - 엔티티 필드명(productName 등)과 응답 필드명(name 등)을 여기서 매핑
   */
  public static ProductResponse from(Product product, ProductTags tags, List<String> tagsFlat) {
    Product.Status status = product.getStatus();
    if (product.isLimitedSale()) {
      status = Product.Status.LIMITED_SALE;
    }
    return fromWithOverrides(product, tags, tagsFlat, null, product.getCostPrice(), status);
  }

  public static ProductResponse fromWithCostPrice(Product product, ProductTags tags, List<String> tagsFlat,
                                                  Integer costPriceOverride) {
    Product.Status status = product.getStatus();
    if (product.isLimitedSale()) {
      status = Product.Status.LIMITED_SALE;
    }
    return fromWithOverrides(product, tags, tagsFlat, null, costPriceOverride, status);
  }

  public static ProductResponse fromWithPrice(Product product, ProductTags tags, List<String> tagsFlat,
                                              Integer priceOverride) {
    Product.Status status = product.getStatus();
    if (product.isLimitedSale()) {
      status = Product.Status.LIMITED_SALE;
    }
    return fromWithOverrides(product, tags, tagsFlat, priceOverride, null, status);
  }

  public static ProductResponse fromWithPriceAndThumbnail(Product product, ProductTags tags, List<String> tagsFlat,
                                                          Integer priceOverride, String thumbnailUrl,
                                                          List<ProductImageResponse> productImages) {
    Product.Status status = product.getStatus();
    if (product.isLimitedSale()) {
      status = Product.Status.LIMITED_SALE;
    }
    Integer resolvedPrice = priceOverride != null ? priceOverride : product.getPrice();
    return new ProductResponse(
            product.getId(),
            product.getSellerId(),
            product.getProductName(),
            product.getShortDesc(),
            product.getDetailHtml(),
            resolvedPrice,
            product.getCostPrice(),
            status,
            product.getStockQty(),
            product.getSafetyStock(),
            tags,
            tagsFlat,
            thumbnailUrl,
            productImages
    );
  }

  private static ProductResponse fromWithOverrides(Product product, ProductTags tags, List<String> tagsFlat,
                                                   Integer priceOverride, Integer costPriceOverride,
                                                   Product.Status status) {
    Integer resolvedCostPrice = costPriceOverride != null ? costPriceOverride : product.getCostPrice();
    Integer resolvedPrice = priceOverride != null ? priceOverride : product.getPrice();
    return new ProductResponse(
            product.getId(),
            product.getSellerId(),
            product.getProductName(), // 엔티티 productName -> 응답 name
            product.getShortDesc(),
            product.getDetailHtml(),
            resolvedPrice,
            resolvedCostPrice,
            status,
            product.getStockQty(),
            product.getSafetyStock(),
            tags,
            tagsFlat
    );
  }

  public static ProductResponse fromForSeller(Product product, ProductTags tags, List<String> tagsFlat) {
    return new ProductResponse(
            product.getId(),
            product.getSellerId(),
            product.getProductName(),
            product.getShortDesc(),
            product.getDetailHtml(),
            product.getPrice(),
            product.getCostPrice(),
            product.getStatus(),
            product.getStockQty(),
            product.getSafetyStock(),
            tags,
            tagsFlat
    );
  }

  public Long getProductId() {
    return productId;
  }

  /**
   * tags 객체의 JSON 필드 출력 순서를 고정 (보기 좋게 + 프론트 기대 순서)
   */
  @JsonPropertyOrder({"space", "tone", "situation", "mood"})
  public static class ProductTags {

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
    public ProductTags(List<String> space, List<String> tone,
                       List<String> situation, List<String> mood) {
      this.space = space == null ? Collections.emptyList() : space;
      this.tone = tone == null ? Collections.emptyList() : tone;
      this.situation = situation == null ? Collections.emptyList() : situation;
      this.mood = mood == null ? Collections.emptyList() : mood;
    }

    /** 빈 tags 기본값 제공 */
    public static ProductTags empty() {
      return new ProductTags(Collections.emptyList(), Collections.emptyList(),
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
}
