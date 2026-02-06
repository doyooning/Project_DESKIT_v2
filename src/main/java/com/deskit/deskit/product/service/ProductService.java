package com.deskit.deskit.product.service;

import com.deskit.deskit.product.dto.ProductCreateRequest;
import com.deskit.deskit.product.dto.ProductCreateResponse;
import com.deskit.deskit.product.dto.ProductBasicUpdateRequest;
import com.deskit.deskit.product.dto.ProductDetailUpdateRequest;
import com.deskit.deskit.product.dto.ProductImageResponse;
import com.deskit.deskit.product.dto.ProductResponse;
import com.deskit.deskit.product.dto.ProductResponse.ProductTags;
import com.deskit.deskit.product.dto.SellerProductListResponse;
import com.deskit.deskit.product.dto.SellerProductDetailResponse;
import com.deskit.deskit.product.dto.SellerProductStatusUpdateRequest;
import com.deskit.deskit.product.dto.SellerProductStatusUpdateResponse;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.entity.ProductImage;
import com.deskit.deskit.product.entity.ProductImage.ImageType;
import com.deskit.deskit.product.repository.ProductImageRepository;
import com.deskit.deskit.product.repository.ProductRepository;
import com.deskit.deskit.product.repository.ProductTagRepository;
import com.deskit.deskit.product.repository.ProductTagRepository.ProductTagRow;
import com.deskit.deskit.livehost.repository.BroadcastProductRepository;
import com.deskit.deskit.livehost.service.AwsS3Service;
import com.deskit.deskit.order.enums.OrderStatus;
import com.deskit.deskit.order.repository.OrderItemRepository;
import com.deskit.deskit.tag.entity.TagCategory.TagCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service // 스프링 서비스 계층 빈 등록 (비즈니스 로직/조합 담당)
public class ProductService {

  private final ProductRepository productRepository; // Product 조회용 JPA Repository
  private final ProductTagRepository productTagRepository; // Product-Tag 매핑 조회용 JPA Repository
  private final ProductImageRepository productImageRepository;
  private final BroadcastProductRepository broadcastProductRepository;
  private final OrderItemRepository orderItemRepository;
  private final AwsS3Service awsS3Service;

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  // 생성자 주입: 테스트/대체 구현에 유리하고, final 필드와 잘 맞음
  public ProductService(ProductRepository productRepository,
                        ProductTagRepository productTagRepository,
                        ProductImageRepository productImageRepository,
                        BroadcastProductRepository broadcastProductRepository,
                        OrderItemRepository orderItemRepository,
                        AwsS3Service awsS3Service) {
    this.productRepository = productRepository;
    this.productTagRepository = productTagRepository;
    this.productImageRepository = productImageRepository;
    this.broadcastProductRepository = broadcastProductRepository;
    this.orderItemRepository = orderItemRepository;
    this.awsS3Service = awsS3Service;
  }

  // 상품 목록 조회: deleted_at IS NULL인 상품만 가져오고, 태그는 productIds로 한 번에 batch 조회 (N+1 방지)
  public List<ProductResponse> getProducts() {
    List<Product> products =
      productRepository.findAllByStatusAndDeletedAtIsNullOrderByIdAsc(Product.Status.ON_SALE);
    if (products.isEmpty()) {
      return Collections.emptyList();
    }

    // 조회된 상품 id 목록 뽑아서 IN 쿼리로 태그를 한 번에 가져오기
    List<Long> productIds = products.stream()
            .map(Product::getId)
            .collect(Collectors.toList());

    Map<Long, String> thumbnailUrls = productImageRepository
      .findAllByProductIdInAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByProductIdAscIdAsc(
        productIds, ImageType.THUMBNAIL, 0
      ).stream()
      .collect(Collectors.toMap(
        ProductImage::getProductId,
        ProductImage::getProductImageUrl,
        (left, right) -> left
      ));

    Map<Long, Integer> livePrices = broadcastProductRepository.findLiveBpPrices(productIds).stream()
            .collect(Collectors.toMap(
                    BroadcastProductRepository.LivePriceRow::getProductId,
                    BroadcastProductRepository.LivePriceRow::getBpPrice,
                    (left, right) -> left
            ));

    // (product_id, tagCode, tagName) 형태의 projection row들
    List<ProductTagRow> rows = productTagRepository.findActiveTagsByProductIds(productIds);

    // productId -> (tags, tagsFlat) 형태로 변환
    Map<Long, TagsBundle> tagsByProductId = buildTagsByProductId(rows);

    // 상품 엔티티 + 태그 번들 => 프론트 호환 DTO로 조립
    return products.stream()
            .map(product -> {
              TagsBundle bundle = tagsByProductId.get(product.getId());
              ProductTags tags = bundle == null ? ProductTags.empty() : bundle.getTags();
              List<String> tagsFlat = bundle == null ? Collections.emptyList() : bundle.getTagsFlat();
              Integer priceOverride = livePrices.get(product.getId());
              String thumbnailUrl = thumbnailUrls.get(product.getId());
              return ProductResponse.fromWithPriceAndThumbnail(
                product, tags, tagsFlat, priceOverride, thumbnailUrl, null
              );
            })
            .collect(Collectors.toList());
  }

  // 상품 단건 조회: deleted_at IS NULL인 상품만 반환. 없으면 Optional.empty()
  public Optional<ProductResponse> getProduct(Long id) {
    Optional<Product> product =
      productRepository.findByIdAndStatusAndDeletedAtIsNull(id, Product.Status.ON_SALE);
    if (product.isEmpty()) {
      return Optional.empty();
    }

    // 단건이지만 동일 로직 재사용: IN(List.of(id))로 tags batch 조회
    List<ProductTagRow> rows = productTagRepository.findActiveTagsByProductIds(List.of(id));
    Map<Long, TagsBundle> tagsByProductId = buildTagsByProductId(rows);

    TagsBundle bundle = tagsByProductId.get(id);
    ProductTags tags = bundle == null ? ProductTags.empty() : bundle.getTags();
    List<String> tagsFlat = bundle == null ? Collections.emptyList() : bundle.getTagsFlat();

    Integer priceOverride = broadcastProductRepository.findLiveBpPriceByProductId(id).stream()
            .findFirst()
            .orElse(null);
    String thumbnailUrl = productImageRepository
      .findFirstByProductIdAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByIdAsc(id, ImageType.THUMBNAIL, 0)
      .map(ProductImage::getProductImageUrl)
      .orElse(null);
    List<ProductImageResponse> productImages = productImageRepository
      .findAllByProductIdAndDeletedAtIsNullOrderBySlotIndexAsc(id).stream()
      .filter(image -> image.getImageType() == ImageType.GALLERY)
      .map(ProductImageResponse::from)
      .collect(Collectors.toList());
    return Optional.of(ProductResponse.fromWithPriceAndThumbnail(
      product.get(), tags, tagsFlat, priceOverride, thumbnailUrl, productImages
    ));
  }

  public List<SellerProductListResponse> getSellerProducts(Long sellerId) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }

    List<Product.Status> statuses = List.of(
      Product.Status.DRAFT,
      Product.Status.READY,
      Product.Status.ON_SALE,
      Product.Status.SOLD_OUT,
      Product.Status.PAUSED,
      Product.Status.HIDDEN
    );

    List<Product> products =
      productRepository.findAllBySellerIdAndStatusInAndDeletedAtIsNullOrderByIdAsc(sellerId, statuses);
    if (products.isEmpty()) {
      return Collections.emptyList();
    }

    List<Long> productIds = products.stream()
      .map(Product::getId)
      .collect(Collectors.toList());

    Map<Long, String> thumbnailUrls = productImageRepository
      .findAllByProductIdInAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByProductIdAscIdAsc(
        productIds, ImageType.THUMBNAIL, 0
      ).stream()
      .collect(Collectors.toMap(
        ProductImage::getProductId,
        ProductImage::getProductImageUrl,
        (left, right) -> left
      ));

    return products.stream()
      .sorted((left, right) -> {
        if (left.getCreatedAt() == null && right.getCreatedAt() == null) {
          return 0;
        }
        if (left.getCreatedAt() == null) {
          return 1;
        }
        if (right.getCreatedAt() == null) {
          return -1;
        }
        return right.getCreatedAt().compareTo(left.getCreatedAt());
      })
      .map(product -> {
        String rawThumbnailUrl = thumbnailUrls.get(product.getId());
        String resolvedThumbnailUrl = resolveImageUrl(rawThumbnailUrl);
        return SellerProductListResponse.from(product, resolvedThumbnailUrl);
      })
      .collect(Collectors.toList());
  }

  private String resolveImageUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
      return value;
    }
    return awsS3Service.buildPublicUrl(value);
  }

  public ProductCreateResponse createProduct(Long sellerId, ProductCreateRequest request) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }

    String productName = request.productName();
    if (productName == null || productName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_name required");
    }
    String shortDesc = request.shortDesc();
    if (shortDesc == null || shortDesc.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "short_desc required");
    }
    String detailHtml = request.detailHtml();
    if (detailHtml == null) {
      detailHtml = "";
    }

    Integer price = request.price();
    if (price == null || price < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be >= 0");
    }
    Integer stockQty = request.stockQty();
    if (stockQty == null || stockQty < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock_qty must be >= 0");
    }

    Integer costPrice = request.costPrice();
    if (costPrice == null) {
      costPrice = 0;
    }
    if (costPrice < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cost_price must be >= 0");
    }

    Product product = new Product(
      sellerId,
      productName,
      shortDesc,
      detailHtml,
      price,
      costPrice,
      stockQty,
      0
    );
    Product saved = productRepository.save(product);
    return ProductCreateResponse.from(saved);
  }

  public SellerProductStatusUpdateResponse updateProductStatus(
    Long sellerId,
    Long productId,
    SellerProductStatusUpdateRequest request
  ) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }
    if (request == null || request.status() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    if (!Objects.equals(product.getSellerId(), sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    try {
      product.changeStatus(request.status());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    Product saved = productRepository.save(product);
    return SellerProductStatusUpdateResponse.from(saved);
  }

  public void updateProductDetailHtml(Long sellerId, Long productId, ProductDetailUpdateRequest request) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }
    if (request == null || request.detailHtml() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "detail_html required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    if (!Objects.equals(product.getSellerId(), sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    try {
      product.changeDetailHtml(request.detailHtml());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    productRepository.save(product);
  }

  public void updateProductBasicInfo(Long sellerId, Long productId, ProductBasicUpdateRequest request) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    if (!Objects.equals(product.getSellerId(), sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    boolean hasBasicFields =
      request.productName() != null
        || request.shortDesc() != null
        || request.price() != null
        || request.stockQty() != null;
    boolean hasDetail = request.detailHtml() != null;
    if (request.imageKeys() != null && request.imageUrls() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_urls required when image_keys present");
    }
    boolean hasImages = request.imageUrls() != null;

    if (!hasBasicFields && !hasDetail && !hasImages) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }

    Product.Status status = product.getStatus();
    if (status != Product.Status.DRAFT
      && status != Product.Status.READY
      && status != Product.Status.PAUSED
      && status != Product.Status.ON_SALE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status not allowed");
    }

    if (status == Product.Status.ON_SALE) {
      if (request.productName() != null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_name not allowed");
      }
      if (request.stockQty() != null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock_qty not allowed");
      }
    }

    boolean triesRestrictedUpdate =
      (request.productName() != null && !Objects.equals(request.productName(), product.getProductName()))
        || (request.shortDesc() != null && !Objects.equals(request.shortDesc(), product.getShortDesc()))
        || (request.price() != null && !Objects.equals(request.price(), product.getPrice()))
        || (request.detailHtml() != null && !Objects.equals(request.detailHtml(), product.getDetailHtml()));

    if (triesRestrictedUpdate) {
      boolean hasPaidOrders = orderItemRepository.existsPaidOrderByProductId(
        productId,
        List.of(OrderStatus.PAID, OrderStatus.COMPLETED)
      );
      if (hasPaidOrders) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "주문이 존재하는 상품은 가격/정보를 수정할 수 없습니다.");
      }
    }

    try {
      if (request.productName() != null) {
        product.updateProductName(request.productName());
      }
      if (request.shortDesc() != null) {
        product.updateShortDesc(request.shortDesc());
      }
      if (request.price() != null) {
        product.updatePrice(request.price());
      }
      if (request.stockQty() != null) {
        product.updateStockQty(request.stockQty());
      }
      if (hasDetail) {
        product.changeDetailHtml(request.detailHtml());
      }
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    if (hasImages) {
      List<String> imageUrls = request.imageUrls();
      List<String> imageKeys = request.imageKeys();
      if (imageUrls.size() != 5) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_urls must be length 5");
      }
      if (imageKeys != null && imageKeys.size() != 5) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_keys must be length 5");
      }
      if (imageKeys != null && imageUrls.size() != imageKeys.size()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_urls and image_keys length mismatch");
      }

      List<ProductImage> existingImages =
        productImageRepository.findAllByProductIdAndDeletedAtIsNullOrderBySlotIndexAsc(productId);
      Map<Integer, ProductImage> existingBySlot = existingImages.stream()
        .filter(image -> image.getSlotIndex() != null)
        .collect(Collectors.toMap(ProductImage::getSlotIndex, image -> image, (left, right) -> left));

      LocalDateTime now = LocalDateTime.now();
      List<ProductImage> toSoftDelete = new ArrayList<>();
      List<ProductImage> toCreate = new ArrayList<>();

      for (int index = 0; index < 5; index += 1) {
        String desiredUrl = imageUrls.get(index);
        if (desiredUrl != null && desiredUrl.isBlank()) {
          desiredUrl = null;
        }
        String desiredKey = null;
        if (imageKeys != null) {
          desiredKey = imageKeys.get(index);
          if (desiredKey != null && desiredKey.isBlank()) {
            desiredKey = null;
          }
        }
        if (desiredUrl == null && desiredKey != null) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_key cannot exist without image_url");
        }

        ProductImage existing = existingBySlot.get(index);
        if (desiredUrl == null) {
          if (existing != null) {
            existing.setDeletedAt(now);
            toSoftDelete.add(existing);
            if (existing.getStoredFileName() != null && !existing.getStoredFileName().isBlank()) {
              try {
                awsS3Service.deleteFile(sellerId, existing.getStoredFileName());
              } catch (RuntimeException ex) {
                log.warn("Failed to delete product image from storage: productId={}, sellerId={}, key={}", productId, sellerId, existing.getStoredFileName(), ex);
              }
            }
          }
          continue;
        }

        if (existing != null) {
          boolean sameUrl = desiredUrl.equals(existing.getProductImageUrl());
          boolean sameKey = Objects.equals(desiredKey, existing.getStoredFileName());
          if (sameUrl && sameKey) {
            continue;
          }
          existing.setDeletedAt(now);
          toSoftDelete.add(existing);
          if (existing.getStoredFileName() != null && !existing.getStoredFileName().isBlank()) {
            try {
              awsS3Service.deleteFile(sellerId, existing.getStoredFileName());
            } catch (RuntimeException ex) {
              log.warn("Failed to delete product image from storage: productId={}, sellerId={}, key={}", productId, sellerId, existing.getStoredFileName(), ex);
            }
          }
        }

        ImageType imageType = index == 0 ? ImageType.THUMBNAIL : ImageType.GALLERY;
        toCreate.add(ProductImage.create(productId, desiredUrl, desiredKey, imageType, index));
      }

      if (!toSoftDelete.isEmpty()) {
        productImageRepository.saveAll(toSoftDelete);
      }
      if (!toCreate.isEmpty()) {
        productImageRepository.saveAll(toCreate);
      }
    }

    productRepository.save(product);
  }

  public SellerProductDetailResponse getSellerProductDetail(Long sellerId, Long productId) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    if (!Objects.equals(product.getSellerId(), sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    List<ProductImage> images = productImageRepository
      .findAllByProductIdAndDeletedAtIsNullOrderBySlotIndexAsc(productId);
    List<String> imageUrls = images.stream()
      .map(ProductImage::getProductImageUrl)
      .collect(Collectors.toList());
    List<String> imageKeys = images.stream()
      .map(ProductImage::getStoredFileName)
      .collect(Collectors.toList());

    return SellerProductDetailResponse.from(product, imageUrls, imageKeys);
  }
  public void completeProductRegistration(Long sellerId, Long productId) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    if (!Objects.equals(product.getSellerId(), sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }
    if (product.getStatus() != Product.Status.DRAFT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be DRAFT");
    }
    if (product.getDetailHtml() == null || product.getDetailHtml().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "detail_html required");
    }

    try {
      product.changeStatus(Product.Status.READY);
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    productRepository.save(product);
  }

  public void softDeleteProduct(Long sellerId, Long productId) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }

    Product product = productRepository.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    if (!Objects.equals(product.getSellerId(), sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }
    if (product.getDeletedAt() != null) {
      return;
    }

    product.setDeletedAt(LocalDateTime.now());
    productRepository.save(product);
  }

  public List<ProductResponse> getProductsByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }

    List<Product> products = productRepository.findAllByIdInAndDeletedAtIsNull(ids);
    if (products.isEmpty()) {
      return Collections.emptyList();
    }

    List<Long> productIds = products.stream()
            .map(Product::getId)
            .collect(Collectors.toList());

    Map<Long, String> thumbnailUrls = productImageRepository
      .findAllByProductIdInAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByProductIdAscIdAsc(
        productIds, ImageType.THUMBNAIL, 0
      ).stream()
      .collect(Collectors.toMap(
        ProductImage::getProductId,
        ProductImage::getProductImageUrl,
        (left, right) -> left
      ));

    List<ProductTagRow> rows = productTagRepository.findActiveTagsByProductIds(productIds);
    Map<Long, TagsBundle> tagsByProductId = buildTagsByProductId(rows);

    return products.stream()
            .map(product -> {
              TagsBundle bundle = tagsByProductId.get(product.getId());
              ProductTags tags = bundle == null ? ProductTags.empty() : bundle.getTags();
              List<String> tagsFlat = bundle == null ? Collections.emptyList() : bundle.getTagsFlat();
              String thumbnailUrl = thumbnailUrls.get(product.getId());
              return ProductResponse.fromWithPriceAndThumbnail(
                product, tags, tagsFlat, null, thumbnailUrl, null
              );
            })
            .collect(Collectors.toList());
  }

  // DB에서 가져온 tag row들을 productId별로 묶어서 tags/tagsFlat을 만든다
  // - TagCode별 리스트 유지(space/tone/situation/mood)
  // - 중복 태그 제거(LinkedHashSet) + 순서 안정적으로 유지
  static Map<Long, TagsBundle> buildTagsByProductId(List<ProductTagRow> rows) {
    Map<Long, TagAccumulator> accumulators = new java.util.HashMap<>();

    for (ProductTagRow row : rows) {
      // projection이 깨졌거나 필수 값이 없으면 스킵 (방어코드)
      if (row == null || row.getProductId() == null || row.getTagCode() == null) {
        continue;
      }

      // productId별 누적기 생성/재사용
      TagAccumulator acc = accumulators.computeIfAbsent(row.getProductId(),
              ignored -> new TagAccumulator());

      // 코드별로 tagName 추가(중복 제거)
      acc.add(row.getTagCode(), row.getTagName());
    }

    // 누적기 -> 최종 응답 번들로 변환
    return accumulators.entrySet().stream()
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().toBundle()
            ));
  }

  // Product 1건에 대한 태그 결과 묶음
  // - tags: 카테고리별 리스트
  // - tagsFlat: UI용 단일 리스트(카테고리 순서대로 합침)
  static class TagsBundle {
    private final ProductTags tags;
    private final List<String> tagsFlat;

    TagsBundle(ProductTags tags, List<String> tagsFlat) {
      this.tags = tags;
      this.tagsFlat = tagsFlat;
    }

    ProductTags getTags() {
      return tags;
    }

    List<String> getTagsFlat() {
      return tagsFlat;
    }
  }

  // TagCode별 태그명을 누적하는 내부 헬퍼
  // - EnumMap: enum 키에 최적화된 Map
  // - LinkedHashSet: 중복 제거 + 입력 순서 유지
  private static class TagAccumulator {
    private final Map<TagCode, LinkedHashSet<String>> byCode = new EnumMap<>(TagCode.class);

    void add(TagCode code, String tagName) {
      if (tagName == null || tagName.isBlank()) {
        return;
      }
      byCode.computeIfAbsent(code, ignored -> new LinkedHashSet<>()).add(tagName);
    }

    // 누적된 데이터를 ProductTags + tagsFlat로 변환
    TagsBundle toBundle() {
      // 프론트가 기대하는 키 순서( space -> tone -> situation -> mood )로 정렬
      List<String> space = listFor(TagCode.SPACE);
      List<String> tone = listFor(TagCode.TONE);
      List<String> situation = listFor(TagCode.SITUATION);
      List<String> mood = listFor(TagCode.MOOD);

      ProductTags tags = new ProductTags(space, tone, situation, mood);

      // flat은 카테고리 순서대로 합치되 중복 제거(LinkedHashSet)
      LinkedHashSet<String> flat = new LinkedHashSet<>();
      addAll(flat, space);
      addAll(flat, tone);
      addAll(flat, situation);
      addAll(flat, mood);

      return new TagsBundle(tags, new ArrayList<>(flat));
    }

    private List<String> listFor(TagCode code) {
      LinkedHashSet<String> values = byCode.get(code);
      if (values == null || values.isEmpty()) {
        return Collections.emptyList();
      }
      return new ArrayList<>(values);
    }

    private void addAll(LinkedHashSet<String> target, List<String> source) {
      if (source == null || source.isEmpty()) {
        return;
      }
      for (String value : source) {
        if (value != null && !value.isBlank()) {
          target.add(value);
        }
      }
    }
  }
}
