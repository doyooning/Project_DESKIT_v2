package com.deskit.deskit.product.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseEntity {

  public enum ImageType {
    THUMBNAIL,
    GALLERY
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "product_image_id", nullable = false)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "product_image_url", nullable = false, length = 500)
  private String productImageUrl;

  @Column(name = "stored_file_name", length = 500)
  private String storedFileName;

  @Enumerated(EnumType.STRING)
  @Column(name = "image_type", nullable = false)
  private ImageType imageType;

  @Column(name = "slot_index", nullable = false)
  private Integer slotIndex;

  public static ProductImage create(Long productId, String productImageUrl, ImageType imageType, Integer slotIndex) {
    return create(productId, productImageUrl, null, imageType, slotIndex);
  }

  public static ProductImage create(Long productId, String productImageUrl, String storedFileName, ImageType imageType, Integer slotIndex) {
    ProductImage image = new ProductImage();
    image.productId = productId;
    image.productImageUrl = productImageUrl;
    image.storedFileName = storedFileName;
    image.imageType = imageType;
    image.slotIndex = slotIndex;
    return image;
  }
}
