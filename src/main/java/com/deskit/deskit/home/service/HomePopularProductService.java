package com.deskit.deskit.home.service;

import com.deskit.deskit.home.dto.HomePopularProductResponse;
import com.deskit.deskit.product.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomePopularProductService {

  private final ProductRepository productRepository;

  public HomePopularProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  public List<HomePopularProductResponse> getPopularProducts(int limit) {
    return productRepository.findPopularProducts(limit).stream()
        .map(HomePopularProductResponse::from)
        .toList();
  }
}
