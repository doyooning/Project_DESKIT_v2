package com.deskit.deskit.product.repository;

import com.deskit.deskit.product.entity.ProductTag;
import com.deskit.deskit.product.entity.ProductTag.ProductTagId;
import com.deskit.deskit.tag.entity.TagCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ProductTag(상품-태그 매핑) 테이블 접근 Repository
 * - PK가 (product_id, tag_id) 복합키라서 ID 타입이 ProductTagId(@EmbeddedId)임
 */
public interface ProductTagRepository extends JpaRepository<ProductTag, ProductTagId> {

  /**
   * 태그 집계를 위한 "조회 결과용 Projection 인터페이스"
   * - 엔티티 전체를 다 가져오지 않고, 필요한 컬럼만 가볍게 조회할 때 사용
   * - 아래 @Query에서 "as productId/tagCode/tagName" 별칭이
   *   이 getter 이름과 매칭되면 Spring Data가 알아서 매핑해줌
   */
  interface ProductTagRow {
    Long getProductId();              // Product의 id
    TagCategory.TagCode getTagCode(); // TagCategory의 enum 코드(SPACE/TONE/...)
    String getTagName();              // Tag의 이름(예: "모던", "미니멀")
  }

  /**
   * 여러 상품(productIds)에 대해 "활성(논리삭제되지 않은) 태그"를 한 번에 조회
   *
   * 포인트:
   * - pt.deletedAt / t.deletedAt / tc.deletedAt 이 모두 null인 데이터만(=활성)
   * - pt.product.id in :productIds 로 한 번에 배치 조회해서 N+1 방지
   * - order by 로 결과 정렬을 고정해서 서비스에서 tags/tagsFlat 만들 때
   *   안정적인 순서(상품 -> 카테고리 -> 태그명)를 유지
   */
  @Query("""
      select pt.product.id as productId,
             tc.tagCode as tagCode,
             t.tagName as tagName
        from ProductTag pt
        join pt.tag t
        join t.tagCategory tc
       where pt.deletedAt is null
         and t.deletedAt is null
         and tc.deletedAt is null
         and pt.product.id in :productIds
      order by pt.product.id, tc.tagCode, t.tagName
      """)
  List<ProductTagRow> findActiveTagsByProductIds(@Param("productIds") List<Long> productIds);

  @Query("""
      select pt.product.id as productId,
             tc.tagCode as tagCode,
             t.tagName as tagName
        from ProductTag pt
        join pt.tag t
        join t.tagCategory tc
       where pt.deletedAt is null
         and t.deletedAt is null
         and tc.deletedAt is null
         and t.tagName in :tagNames
       order by pt.product.id, tc.tagCode, t.tagName
      """)
  List<ProductTagRow> findActiveTagsByTagNames(@Param("tagNames") List<String> tagNames);
  void deleteByProduct_Id(Long productId);
}
