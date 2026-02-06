package com.deskit.deskit.product.repository;

import com.deskit.deskit.product.entity.ProductImage;
import com.deskit.deskit.product.entity.ProductImage.ImageType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

  long countByProductIdAndDeletedAtIsNull(Long productId);

  boolean existsByProductIdAndImageTypeAndSlotIndexAndDeletedAtIsNull(
    Long productId,
    ImageType imageType,
    Integer slotIndex
  );

  Optional<ProductImage> findFirstByProductIdAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByIdAsc(
    Long productId,
    ImageType imageType,
    Integer slotIndex
  );

  List<ProductImage> findAllByProductIdInAndImageTypeAndSlotIndexAndDeletedAtIsNullOrderByProductIdAscIdAsc(
    List<Long> productIds,
    ImageType imageType,
    Integer slotIndex
  );

  List<ProductImage> findAllByProductIdAndDeletedAtIsNullOrderBySlotIndexAsc(Long productId);
}
