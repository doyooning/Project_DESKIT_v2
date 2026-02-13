package com.deskit.deskit.order.controller;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.order.dto.CreateOrderRequest;
import com.deskit.deskit.order.dto.CreateOrderResponse;
import com.deskit.deskit.order.dto.OrderCancelRequest;
import com.deskit.deskit.order.dto.OrderCancelResponse;
import com.deskit.deskit.order.dto.OrderDetailResponse;
import com.deskit.deskit.order.dto.OrderSummaryResponse;
import com.deskit.deskit.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orderService;
  private final MemberRepository memberRepository;

  public OrderController(OrderService orderService, MemberRepository memberRepository) {
    this.orderService = orderService;
    this.memberRepository = memberRepository;
  }

  @PostMapping
  public ResponseEntity<CreateOrderResponse> createOrder(
          @AuthenticationPrincipal CustomOAuth2User user,
          @Valid @RequestBody CreateOrderRequest request
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(orderService.createOrder(memberId, request));
  }

  @GetMapping
  public ResponseEntity<List<OrderSummaryResponse>> getMyOrders(
          @AuthenticationPrincipal CustomOAuth2User user
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(orderService.getMyOrders(memberId));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<OrderDetailResponse> getMyOrderDetail(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("orderId") Long orderId
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(orderService.getMyOrderDetail(memberId, orderId));
  }

  @RequestMapping(value = "/{orderId}/cancel", method = {RequestMethod.PATCH, RequestMethod.POST})
  public ResponseEntity<OrderCancelResponse> requestCancel(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("orderId") Long orderId,
          @Valid @RequestBody OrderCancelRequest request
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(orderService.requestCancel(memberId, orderId, request));
  }

  @PostMapping("/{orderId}/abandon")
  public ResponseEntity<Void> abandonCreatedOrder(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("orderId") Long orderId
  ) {
    Long memberId = resolveMemberId(user);
    orderService.abandonCreatedOrder(memberId, orderId);
    return ResponseEntity.noContent().build();
  }

  private Long resolveMemberId(CustomOAuth2User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    Long memberId = tryExtractMemberId(user);
    if (memberId != null) {
      return memberId;
    }

    String loginId = user.getUsername();
    if (loginId == null || loginId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    Member member = memberRepository.findByLoginId(loginId);
    if (member == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    return member.getMemberId();
  }

  private Long tryExtractMemberId(CustomOAuth2User user) {
    Map<String, Object> attributes = user.getAttributes();
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }

    Object value = attributes.get("memberId");
    if (value == null) {
      value = attributes.get("member_id");
    }
    if (value == null) {
      value = attributes.get("id");
    }

    if (value instanceof Number) {
      return ((Number) value).longValue();
    }

    if (value instanceof String) {
      String text = ((String) value).trim();
      if (text.isEmpty()) {
        return null;
      }
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ex) {
        return null;
      }
    }

    return null;
  }
}
