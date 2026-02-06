package com.deskit.deskit.product.repository;

import com.deskit.deskit.product.entity.Product;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Product 엔티티에 대한 DB 접근 레이어(Repository)
 * - Spring Data JPA가 구현체를 자동 생성해줌
 * - 기본 CRUD(save/findById/findAll/delete 등)는 JpaRepository가 제공
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

  /**
   * deleted_at 이 NULL(= 논리삭제 안 된 데이터)인 상품만 조회
   * 그리고 id 오름차순으로 정렬해서 리스트로 반환
   *
   * 메서드 이름 규칙(쿼리 메서드)로 SQL/JPQL을 자동 생성:
   * - findAllByDeletedAtIsNull : deletedAt 컬럼이 null인 것만
   * - OrderByIdAsc : id 기준 오름차순 정렬
   */
  List<Product> findAllByDeletedAtIsNullOrderByIdAsc();

  List<Product> findAllByStatusAndDeletedAtIsNullOrderByIdAsc(Product.Status status);

  List<Product> findAllBySellerIdAndStatusInAndDeletedAtIsNullOrderByIdAsc(
    Long sellerId,
    List<Product.Status> statuses
  );

  /**
   * 특정 id의 상품을 조회하되, deleted_at 이 NULL인 경우만 조회
   * 결과가 없을 수 있으니 Optional로 감쌈
   *
   * 예: id는 존재하지만 deleted_at이 채워져 있으면(논리삭제) -> Optional.empty()
   */
  Optional<Product> findByIdAndDeletedAtIsNull(Long id);

  List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

  Optional<Product> findByIdAndStatusAndDeletedAtIsNull(Long id, Product.Status status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id and p.deletedAt is null")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id and p.status = :status and p.deletedAt is null")
  Optional<Product> findByIdForUpdateAndStatus(@Param("id") Long id,
                                               @Param("status") Product.Status status);

  @Query(value = """
      SELECT
          p.product_id AS productId,
          p.product_name AS productName,
          p.price AS price,
          COALESCE(SUM(CASE WHEN o.order_id IS NOT NULL THEN oi.quantity ELSE 0 END), 0) AS soldQty,
          pi.product_image_url AS thumbnailUrl,
          MAX(p.created_at) AS createdAt
      FROM product p
      LEFT JOIN order_item oi
          ON oi.product_id = p.product_id
          AND oi.deleted_at IS NULL
      LEFT JOIN `order` o
          ON o.order_id = oi.order_id
          AND o.deleted_at IS NULL
          AND o.status IN ('PAID', 'COMPLETED')
      LEFT JOIN product_image pi
          ON pi.product_id = p.product_id
          AND pi.image_type = 'THUMBNAIL'
          AND pi.slot_index = 0
          AND pi.deleted_at IS NULL
      WHERE p.deleted_at IS NULL
      GROUP BY p.product_id, p.product_name, p.price, pi.product_image_url
      ORDER BY soldQty DESC, createdAt DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<PopularProductRow> findPopularProducts(@Param("limit") int limit);

  interface PopularProductRow {
    Long getProductId();
    String getProductName();
    Integer getPrice();
    Long getSoldQty();
    String getThumbnailUrl();
  }
}
