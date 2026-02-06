package com.deskit.deskit.cart.controller;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.cart.dto.CartItemCreateRequest;
import com.deskit.deskit.cart.dto.CartItemUpdateRequest;
import com.deskit.deskit.cart.dto.CartResponse;
import com.deskit.deskit.cart.service.CartService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController // REST API 컨트롤러(반환값이 JSON으로 직렬화됨)
@RequestMapping("/api/cart") // 장바구니 API 기본 경로
public class CartController {

  private final CartService cartService; // 장바구니 비즈니스 로직 담당
  private final MemberRepository memberRepository; // 로그인 유저(loginId) → member 조회용

  // 생성자 주입(스프링이 Bean 주입)
  public CartController(CartService cartService, MemberRepository memberRepository) {
    this.cartService = cartService;
    this.memberRepository = memberRepository;
  }

  /**
   * 장바구니 조회
   * GET /api/cart
   * - 로그인 사용자 기준으로 본인 장바구니 + 아이템 목록을 내려줌
   */
  @GetMapping
  public CartResponse getCart(@AuthenticationPrincipal CustomOAuth2User user) {
    // 인증된 사용자로부터 memberId를 찾아냄(아래 resolveMemberId 참고)
    Long memberId = resolveMemberId(user);
    return cartService.getCart(memberId);
  }

  /**
   * 장바구니 아이템 추가
   * POST /api/cart/items
   * body: { product_id, quantity }
   * - 같은 product가 이미 담겨있으면 service에서 수량 누적 처리
   */
  @PostMapping("/items")
  public CartResponse addItem(
          @AuthenticationPrincipal CustomOAuth2User user,
          @Valid @RequestBody CartItemCreateRequest request // @Valid로 DTO validation(@NotNull, @Positive 등) 적용
  ) {
    Long memberId = resolveMemberId(user);
    return cartService.addItem(memberId, request.getProductId(), request.getQuantity());
  }

  /**
   * 장바구니 아이템 수량 변경
   * PATCH /api/cart/items/{cartItemId}
   * body: { quantity }
   * - 본인 장바구니에 속한 cartItem인지 service에서 검증
   */
  @PatchMapping("/items/{cartItemId}")
  public CartResponse updateItem(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("cartItemId") Long cartItemId, // URL path에서 cartItemId 추출
          @Valid @RequestBody CartItemUpdateRequest request
  ) {
    Long memberId = resolveMemberId(user);
    return cartService.updateItemQuantity(memberId, cartItemId, request.getQuantity());
  }

  /**
   * 장바구니 아이템 삭제(소프트 삭제)
   * DELETE /api/cart/items/{cartItemId}
   * - 성공 시 204 No Content 반환
   */
  @DeleteMapping("/items/{cartItemId}")
  public ResponseEntity<Void> deleteItem(
          @AuthenticationPrincipal CustomOAuth2User user,
          @PathVariable("cartItemId") Long cartItemId
  ) {
    Long memberId = resolveMemberId(user);
    cartService.deleteItem(memberId, cartItemId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 현재 요청의 인증 사용자(CustomOAuth2User)에서 memberId를 알아내는 헬퍼.
   *
   * 1) user가 null이면 인증이 안된 상태 → 401
   * 2) user.attributes에서 memberId 후보 키로 먼저 꺼내봄(memberId/member_id/id)
   * 3) attributes에 없으면 username(loginId)로 member를 DB 조회해서 memberId를 가져옴
   */
  private Long resolveMemberId(CustomOAuth2User user) {
    if (user == null) {
      // 스프링 시큐리티 컨텍스트에 인증 정보가 없으면 여기로 옴
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    // attributes에서 바로 memberId를 뽑아낼 수 있으면(토큰/세션에 담겨있는 경우) DB 조회를 피함
    Long memberId = tryExtractMemberId(user);
    if (memberId != null) {
      return memberId;
    }

    // attributes에 없으면 username을 loginId로 보고 DB에서 member를 찾는다
    String loginId = user.getUsername();
    if (loginId == null || loginId.isBlank()) {
      // 로그인 식별자가 없으면 member를 찾을 수 없으니 404로 처리(프로젝트 정책에 맞춘 것)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    // loginId로 회원 조회 (주의: 레포 시그니처가 Optional이면 여기 코드가 달라져야 함)
    Member member = memberRepository.findByLoginId(loginId);
    if (member == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    return member.getMemberId();
  }

  /**
   * CustomOAuth2User의 attributes(Map)에서 memberId를 최대한 유연하게 추출하는 메서드.
   *
   * - provider/구현체마다 키 이름이 달라질 수 있어서 후보 키를 여러 개 확인
   * - 값 타입이 Number일 수도, String일 수도 있어서 둘 다 처리
   * - 파싱 실패/없으면 null 반환 → resolveMemberId에서 DB 조회 fallback
   */
  private Long tryExtractMemberId(CustomOAuth2User user) {
    Map<String, Object> attributes = user.getAttributes();
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }

    // 후보 키 순서대로 조회
    Object value = attributes.get("memberId");
    if (value == null) {
      value = attributes.get("member_id");
    }
    if (value == null) {
      value = attributes.get("id");
    }

    // 숫자 타입이면 그대로 long 변환
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }

    // 문자열이면 trim 후 Long 파싱
    if (value instanceof String) {
      String text = ((String) value).trim();
      if (text.isEmpty()) {
        return null;
      }
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ex) {
        // 숫자로 파싱 불가하면 memberId로 쓸 수 없으므로 null
        return null;
      }
    }

    // 그 외 타입(예: Map 등)이면 memberId로 못 쓰니 null
    return null;
  }
}