package com.deskit.deskit.product.controller;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.product.dto.ProductCreateRequest;
import com.deskit.deskit.product.dto.ProductCreateResponse;
import com.deskit.deskit.product.dto.ProductBasicUpdateRequest;
import com.deskit.deskit.product.dto.ProductDetailUpdateRequest;
import com.deskit.deskit.product.dto.ProductImageResponse;
import com.deskit.deskit.product.dto.ProductImageUploadResponse;
import com.deskit.deskit.product.dto.SellerProductDetailResponse;
import com.deskit.deskit.product.dto.SellerProductListResponse;
import com.deskit.deskit.product.dto.SellerProductStatusUpdateRequest;
import com.deskit.deskit.product.dto.SellerProductStatusUpdateResponse;
import com.deskit.deskit.product.dto.ProductTagUpdateRequest;
import com.deskit.deskit.livehost.common.enums.UploadType;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.dto.response.ImageUploadResponse;
import com.deskit.deskit.livehost.service.AwsS3Service;
import com.deskit.deskit.product.entity.ProductImage.ImageType;
import com.deskit.deskit.product.service.ProductImageService;
import com.deskit.deskit.product.service.ProductService;
import com.deskit.deskit.product.service.ProductTagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seller/products")
public class SellerProductController {

  private final ProductService productService;
  private final ProductImageService productImageService;
  private final ProductTagService productTagService;
  private final SellerRepository sellerRepository;
  private final AwsS3Service awsS3Service;

  public SellerProductController(ProductService productService,
                                 ProductImageService productImageService,
                                 ProductTagService productTagService,
                                 SellerRepository sellerRepository,
                                 AwsS3Service awsS3Service) {
    this.productService = productService;
    this.productImageService = productImageService;
    this.productTagService = productTagService;
    this.sellerRepository = sellerRepository;
    this.awsS3Service = awsS3Service;
  }

  @PostMapping
  public ResponseEntity<ProductCreateResponse> createProduct(
          @AuthenticationPrincipal CustomOAuth2User user,
          @Valid @RequestBody ProductCreateRequest request
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(productService.createProduct(sellerId, request));
  }

  @PutMapping("/{productId}")
  public ResponseEntity<Void> updateProductBasicInfo(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId,
          @Valid @RequestBody ProductBasicUpdateRequest request
  ) {
    Long sellerId = resolveSellerId(user);
    productService.updateProductBasicInfo(sellerId, productId, request);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  public ResponseEntity<List<SellerProductListResponse>> getSellerProducts(
          @AuthenticationPrincipal CustomOAuth2User user
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(productService.getSellerProducts(sellerId));
  }

  @GetMapping("/{productId}")
  public ResponseEntity<SellerProductDetailResponse> getSellerProductDetail(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(productService.getSellerProductDetail(sellerId, productId));
  }

  @PatchMapping("/{productId}/status")
  public ResponseEntity<SellerProductStatusUpdateResponse> updateProductStatus(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId,
          @Valid @RequestBody SellerProductStatusUpdateRequest request
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(productService.updateProductStatus(sellerId, productId, request));
  }

  @PatchMapping("/{productId}/detail")
  public ResponseEntity<Void> updateProductDetail(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId,
          @Valid @RequestBody ProductDetailUpdateRequest request
  ) {
    Long sellerId = resolveSellerId(user);
    productService.updateProductDetailHtml(sellerId, productId, request);
    return ResponseEntity.ok().build();
  }

  @PatchMapping("/{productId}/complete")
  public ResponseEntity<Void> completeProductRegistration(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId
  ) {
    Long sellerId = resolveSellerId(user);
    productService.completeProductRegistration(sellerId, productId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{productId}/images")
  public ResponseEntity<ProductImageResponse> uploadProductImage(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId,
          @RequestParam("file") MultipartFile file,
          @RequestParam("imageType") ImageType imageType,
          @RequestParam("slotIndex") Integer slotIndex
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(
      productImageService.uploadImage(sellerId, productId, file, imageType, slotIndex)
    );
  }

  @PostMapping(value = "/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProductImageUploadResponse> uploadProductImageFile(
          @AuthenticationPrincipal CustomOAuth2User user,
          @RequestPart("file") MultipartFile file
  ) {
    Long sellerId = resolveSellerId(user);
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid file type");
    }
    try {
      ImageUploadResponse response = awsS3Service.uploadFile(sellerId, file, UploadType.PRODUCT_IMAGE);
      return ResponseEntity.ok(
        new ProductImageUploadResponse(response.getFileUrl(), response.getStoredFileName())
      );
    } catch (BusinessException ex) {
      throw new ResponseStatusException(ex.getErrorCode().getStatus(), ex.getErrorCode().getMessage());
    }
  }

  @PutMapping("/{productId}/tags")
  public ResponseEntity<Void> updateProductTags(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId,
          @Valid @RequestBody ProductTagUpdateRequest request
  ) {
    Long sellerId = resolveSellerId(user);
    productTagService.updateProductTags(sellerId, productId, request);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{productId}")
  public ResponseEntity<Void> deleteProduct(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("productId") Long productId
  ) {
    Long sellerId = resolveSellerId(user);
    productService.softDeleteProduct(sellerId, productId);
    return ResponseEntity.noContent().build();
  }

  private Long resolveSellerId(CustomOAuth2User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    String role = user.getAuthorities().iterator().next().getAuthority();
    if (role == null || !role.startsWith("ROLE_SELLER")) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller role required");
    }

    String loginId = user.getUsername();
    if (loginId == null || loginId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller not found");
    }

    Seller seller = sellerRepository.findByLoginId(loginId);
    if (seller == null || seller.getSellerId() == null) {
      String email = user.getEmail();
      if (email == null || email.isBlank()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller not found");
      }
      seller = sellerRepository.findByLoginId(email);
      if (seller == null || seller.getSellerId() == null) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller not found");
      }
    }
    if (seller.getStatus() != SellerStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller inactive");
    }

    return seller.getSellerId();
  }
}
