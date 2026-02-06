package com.deskit.deskit.livehost.dto.response;

import com.deskit.deskit.livehost.common.enums.BroadcastProductStatus;
import com.deskit.deskit.livehost.entity.BroadcastProduct;
import com.deskit.deskit.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BroadcastProductResponse {
    private Long bpId;            // 방송상품 ID (PK)
    private Long productId;       // 원본 상품 ID
    private String name;          // 상품명 (Product API 연동 필요)
    private String imageUrl;      // 상품 이미지 (Product API 연동 필요)
    private int originalPrice;    // 정가(원가)
    private Integer originalCostPrice; // 방송 시작 전 판매가
    private int stockQty;         // 방송 판매 수량 기준 재고
    private int safetyStock;      // 안전 재고
    private int productStockQty;  // 상품 원본 재고 수량

    private int bpPrice;        // 라이브 특가 (bp_price)
    private int bpQuantity;     // 판매 수량 (bp_quantity)
    private int displayOrder;     // 노출 순서
    private boolean isPinned;     // 핀 고정 여부 (Y/N)
    private BroadcastProductStatus status;        // 상품 상태 (SELLING, SOLDOUT, DELETED)

    public static BroadcastProductResponse fromEntity(BroadcastProduct bp) {
        return fromEntity(bp, bp.getBpQuantity(), null);
    }

    public static BroadcastProductResponse fromEntity(BroadcastProduct bp, Integer remainingQuantity) {
        return fromEntity(bp, remainingQuantity, null);
    }

    public static BroadcastProductResponse fromEntity(BroadcastProduct bp, Integer remainingQuantity, Integer originalCostPrice) {
        return fromEntityWithImageUrl(bp, remainingQuantity, originalCostPrice, null);
    }

    public static BroadcastProductResponse fromEntityWithImageUrl(BroadcastProduct bp, Integer remainingQuantity,
                                                                  Integer originalCostPrice, String imageUrl) {
        Product p = bp.getProduct();
        int remaining = remainingQuantity != null ? remainingQuantity : bp.getBpQuantity();

        return BroadcastProductResponse.builder()
                .bpId(bp.getBpId())
                .productId(p.getId())
                .name(p.getProductName())
                .imageUrl(imageUrl)
                .originalPrice(p.getCostPrice())
                .originalCostPrice(originalCostPrice)
                .stockQty(remaining)
                .safetyStock(p.getSafetyStock())
                .productStockQty(p.getStockQty())
                .bpPrice(bp.getBpPrice())
                .bpQuantity(bp.getBpQuantity())
                .displayOrder(bp.getDisplayOrder())
                .isPinned(bp.isPinned())
                .status(bp.getStatus())
                .build();
    }
}


// Product 엔티티 헬퍼 메서드 추가

//// [추가] 이미지와 1:N 관계 설정
//@OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
//@Builder.Default
//private List<ProductImage> images = new ArrayList<>();


//// [추가] 썸네일 URL을 바로 꺼내주는 편의 메서드 추가
//public String getThumbnailUrl() {
//    if (this.images == null || this.images.isEmpty()) {
//        return null; // 이미지가 없으면 null 반환 (또는 기본 이미지 URL)
//    }
//
//    return this.images.stream()
//            .filter(img -> img.getImageType() == ImageType.THUMBNAIL) // THUMBNAIL 타입 필터링
//            .findFirst()
//            .map(ProductImage::getProductImageUrl)
//            .orElse(null); // 없으면 null
//}
