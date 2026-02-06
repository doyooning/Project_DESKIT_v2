package com.deskit.deskit.product.controller;

import com.deskit.deskit.product.dto.ProductResponse;
import com.deskit.deskit.product.service.ProductService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product 조회 전용 REST API 컨트롤러
 * - /api/products: 상품 목록 조회
 * - /api/products/{id}: 상품 단건 조회
 *
 * 주의:
 * - 실제 인증/인가(Spring Security) 설정에 따라 이 엔드포인트도 로그인 리다이렉트(302)가 발생할 수 있음.
 * - 여기서는 비즈니스 로직을 직접 처리하지 않고 ProductService에 위임해서 계층 분리를 유지함.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  // 조회 로직(상품 + 태그 집계)을 담당하는 서비스
  private final ProductService productService;

  // 생성자 주입(스프링이 ProductService 빈을 주입)
  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  /**
   * 상품 목록 조회
   * - Service에서 deletedAt is null(소프트 삭제 제외) 필터 + 태그 배치 조회 후 DTO로 변환
   */
  @GetMapping
  public List<ProductResponse> getProducts() {
    return productService.getProducts();
  }

  /**
   * 상품 단건 조회
   * - 존재하면 200 OK + ProductResponse
   * - 없으면 404 Not Found
   */
  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> getProduct(@PathVariable("id") Long id) {
    return productService.getProduct(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
  }
}