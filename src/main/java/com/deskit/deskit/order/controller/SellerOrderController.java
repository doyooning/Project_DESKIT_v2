package com.deskit.deskit.order.controller;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.order.dto.SellerOrderDetailResponse;
import com.deskit.deskit.order.dto.SellerOrderSummaryResponse;
import com.deskit.deskit.order.enums.OrderStatus;
import com.deskit.deskit.order.service.SellerOrderService;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/seller/orders")
public class SellerOrderController {

  private final SellerOrderService sellerOrderService;
  private final SellerRepository sellerRepository;

  public SellerOrderController(
    SellerOrderService sellerOrderService,
    SellerRepository sellerRepository
  ) {
    this.sellerOrderService = sellerOrderService;
    this.sellerRepository = sellerRepository;
  }

  @GetMapping
  public ResponseEntity<Page<SellerOrderSummaryResponse>> getSellerOrders(
    @AuthenticationPrincipal CustomOAuth2User user,
    @RequestParam(name = "status", required = false) OrderStatus status,
    @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(sellerOrderService.getSellerOrders(sellerId, status, pageable));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<SellerOrderDetailResponse> getSellerOrderDetail(
    @AuthenticationPrincipal CustomOAuth2User user,
    @PathVariable("orderId") Long orderId
  ) {
    Long sellerId = resolveSellerId(user);
    return ResponseEntity.ok(sellerOrderService.getSellerOrderDetail(sellerId, orderId));
  }

  private Long resolveSellerId(CustomOAuth2User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    boolean isSeller = user.getAuthorities().stream()
      .anyMatch(authority -> authority != null &&
        authority.getAuthority() != null &&
        authority.getAuthority().startsWith("ROLE_SELLER"));
    if (!isSeller) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller role required");
    }

    String loginId = normalize(user.getUsername());
    String email = normalize(user.getEmail());
    Seller seller = loginId == null ? null : sellerRepository.findByLoginId(loginId);
    if (seller == null && email != null && !Objects.equals(loginId, email)) {
      seller = sellerRepository.findByLoginId(email);
    }

    if (seller == null || seller.getSellerId() == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller not found");
    }
    if (seller.getStatus() != SellerStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller inactive");
    }

    return seller.getSellerId();
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
