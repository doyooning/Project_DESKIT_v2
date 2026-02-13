package com.deskit.deskit.product.repository;

import com.deskit.deskit.product.entity.ProductTag;
import com.deskit.deskit.product.entity.ProductTag.ProductTagId;
import com.deskit.deskit.tag.entity.TagCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductTagRepository extends JpaRepository<ProductTag, ProductTagId> {

  interface ProductTagRow {
    Long getProductId();
    Long getTagId();
    TagCategory.TagCode getTagCode();
    String getTagName();
  }

  @Query("""
      select pt.product.id as productId,
             t.id as tagId,
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
             t.id as tagId,
             tc.tagCode as tagCode,
             t.tagName as tagName
        from ProductTag pt
        join pt.tag t
        join t.tagCategory tc
       where pt.deletedAt is null
         and t.deletedAt is null
         and tc.deletedAt is null
         and t.id in :tagIds
       order by pt.product.id, tc.tagCode, t.tagName
      """)
  List<ProductTagRow> findActiveTagsByTagIds(@Param("tagIds") List<Long> tagIds);

  @Query("""
      select pt.product.id as productId,
             t.id as tagId,
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
