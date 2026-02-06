package com.deskit.deskit.product.service;

import com.deskit.deskit.product.dto.ProductImageResponse;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.entity.ProductImage;
import com.deskit.deskit.product.entity.ProductImage.ImageType;
import com.deskit.deskit.product.repository.ProductImageRepository;
import com.deskit.deskit.product.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductImageService {

  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final S3Uploader s3Uploader;

  public ProductImageService(ProductRepository productRepository,
                             ProductImageRepository productImageRepository,
                             S3Uploader s3Uploader) {
    this.productRepository = productRepository;
    this.productImageRepository = productImageRepository;
    this.s3Uploader = s3Uploader;
  }

  public ProductImageResponse uploadImage(Long sellerId,
                                          Long productId,
                                          MultipartFile file,
                                          ImageType imageType,
                                          Integer slotIndex) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
    }
    if (imageType == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageType required");
    }
    if (slotIndex == null || slotIndex < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotIndex required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    if (!product.getSellerId().equals(sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    long currentCount = productImageRepository.countByProductIdAndDeletedAtIsNull(productId);
    if (currentCount >= 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "max 5 images per product");
    }

    if (productImageRepository.existsByProductIdAndImageTypeAndSlotIndexAndDeletedAtIsNull(
      productId, imageType, slotIndex)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image slot already used");
    }

    String keyPrefix = "seller/" + sellerId
      + "/products/" + productId
      + "/" + imageType.name().toLowerCase()
      + "_" + slotIndex;
    String imageUrl = s3Uploader.upload(keyPrefix, file);

    ProductImage image = ProductImage.create(productId, imageUrl, imageType, slotIndex);
    ProductImage saved = productImageRepository.save(image);
    return ProductImageResponse.from(saved);
  }
}
