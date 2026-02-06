package com.deskit.deskit.setup.repository;

import com.deskit.deskit.setup.entity.Setup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetupRepository extends JpaRepository<Setup, Long> {

  List<Setup> findAllByDeletedAtIsNullOrderByIdAsc();

  Optional<Setup> findByIdAndDeletedAtIsNull(Long id);

  @Query(value = """
      select sp.product_id
      from setup_product sp
      where sp.setup_id = :setupId
      """, nativeQuery = true)
  List<Long> findProductIdsBySetupId(@Param("setupId") Long setupId);

  @Query(value = """
      SELECT
          s.setup_id AS setupId,
          s.setup_name AS setupName,
          s.short_desc AS shortDesc,
          s.setup_image_url AS imageUrl,
          COALESCE(SUM(CASE WHEN o.order_id IS NOT NULL THEN oi.quantity ELSE 0 END), 0) AS soldQty,
          MAX(s.created_at) AS createdAt
      FROM setup s
      LEFT JOIN setup_product sp
          ON sp.setup_id = s.setup_id
          AND sp.deleted_at IS NULL
      LEFT JOIN order_item oi
          ON oi.product_id = sp.product_id
          AND oi.deleted_at IS NULL
      LEFT JOIN `order` o
          ON o.order_id = oi.order_id
          AND o.deleted_at IS NULL
          AND o.status IN ('PAID', 'COMPLETED')
      WHERE s.deleted_at IS NULL
      GROUP BY s.setup_id, s.setup_name, s.short_desc, s.setup_image_url
      ORDER BY soldQty DESC, createdAt DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<PopularSetupRow> findPopularSetups(@Param("limit") int limit);

  interface PopularSetupRow {
    Long getSetupId();
    String getSetupName();
    String getShortDesc();
    String getImageUrl();
    Long getSoldQty();
  }
}
