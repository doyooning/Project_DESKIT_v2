package com.deskit.deskit.product.service;

import com.deskit.deskit.product.dto.ProductTagUpdateRequest;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.entity.ProductTag;
import com.deskit.deskit.product.repository.ProductRepository;
import com.deskit.deskit.product.repository.ProductTagRepository;
import com.deskit.deskit.tag.entity.Tag;
import com.deskit.deskit.tag.repository.TagRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductTagService {

  private final ProductRepository productRepository;
  private final ProductTagRepository productTagRepository;
  private final TagRepository tagRepository;

  public ProductTagService(ProductRepository productRepository,
                           ProductTagRepository productTagRepository,
                           TagRepository tagRepository) {
    this.productRepository = productRepository;
    this.productTagRepository = productTagRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public void updateProductTags(Long sellerId, Long productId, ProductTagUpdateRequest request) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller_id required");
    }
    if (productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
    }
    if (request == null || request.tagIds() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tag_ids required");
    }

    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    if (!product.getSellerId().equals(sellerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    List<Long> tagIds = request.tagIds();
    Set<Long> uniqueIds = new LinkedHashSet<>(tagIds);

    if (uniqueIds.isEmpty()) {
      productTagRepository.deleteByProduct_Id(productId);
      return;
    }

    List<Tag> tags = tagRepository.findAllById(uniqueIds);
    if (tags.size() != uniqueIds.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid tag_id");
    }
    for (Tag tag : tags) {
      if (tag.getDeletedAt() != null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid tag_id");
      }
    }

    productTagRepository.deleteByProduct_Id(productId);

    List<ProductTag> mappings = new ArrayList<>();
    for (Tag tag : tags) {
      ProductTag productTag = new ProductTag(product, tag);
      productTag.setId(new ProductTag.ProductTagId(product.getId(), tag.getId()));
      mappings.add(productTag);
    }
    productTagRepository.saveAll(mappings);
  }
}
