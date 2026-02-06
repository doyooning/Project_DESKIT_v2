package com.deskit.deskit.cart.service;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.cart.dto.CartItemResponse;
import com.deskit.deskit.cart.dto.CartResponse;
import com.deskit.deskit.cart.entity.Cart;
import com.deskit.deskit.cart.entity.CartItem;
import com.deskit.deskit.cart.repository.CartItemRepository;
import com.deskit.deskit.cart.repository.CartRepository;
import com.deskit.deskit.livehost.repository.BroadcastProductRepository;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional // 이 서비스의 public 메서드들은 기본적으로 트랜잭션 안에서 동작(조회/수정 일관성 보장)
public class CartService {

  private final CartRepository cartRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final BroadcastProductRepository broadcastProductRepository;
  private final MemberRepository memberRepository;
  private final EntityManager entityManager; // getReference()로 Member 프록시를 만들 때 사용

  public CartService(CartRepository cartRepository,
                     CartItemRepository cartItemRepository,
                     ProductRepository productRepository,
                     BroadcastProductRepository broadcastProductRepository,
                     MemberRepository memberRepository,
                     EntityManager entityManager) {
    this.cartRepository = cartRepository;
    this.cartItemRepository = cartItemRepository;
    this.productRepository = productRepository;
    this.broadcastProductRepository = broadcastProductRepository;
    this.memberRepository = memberRepository;
    this.entityManager = entityManager;
  }

  /**
   * 회원의 장바구니를 가져오거나(있으면),
   * 없으면 새로 만들어 저장한 뒤 반환한다.
   *
   * - cart 테이블은 member_id UNIQUE라서 회원당 1개만 존재해야 함
   * - deleted_at != null 인 장바구니는 논리삭제로 간주하고 제외
   */
  public Cart getOrCreateCart(Long memberId) {
    return cartRepository.findByMember_MemberIdAndDeletedAtIsNull(memberId)
            .orElseGet(() -> cartRepository.save(new Cart(getMemberReference(memberId))));
  }

  /**
   * 장바구니 조회 API에서 사용할 응답 DTO 생성.
   * - 장바구니가 없으면 생성 후 빈 items로 내려갈 수 있음(현재 로직은 무조건 생성)
   * - cart_item은 deleted_at null만 조회
   */
  public CartResponse getCart(Long memberId) {
    Cart cart = getOrCreateCart(memberId);

    // cart_id 기준으로 아이템 목록을 가져와 DTO로 변환
    List<CartItemResponse> items = cartItemRepository
            .findAllByCart_IdAndDeletedAtIsNullOrderByIdAsc(cart.getId())
            .stream()
            .map(item -> {
              int currentPrice = resolveCurrentPrice(item.getProduct());
              if (!Integer.valueOf(currentPrice).equals(item.getPriceSnapshot())) {
                item.updatePriceSnapshot(currentPrice);
              }
              return CartItemResponse.from(item);
            })
            .collect(Collectors.toList());

    return new CartResponse(cart.getId(), items);
  }

  /**
   * 장바구니에 상품 추가.
   * - quantity 검증(>=1)
   * - 상품 존재 여부 확인(+ deleted_at null)
   * - 동일 상품이 이미 담겨있으면 quantity 누적
   * - 없으면 새 CartItem 생성(담을 당시 가격을 price_snapshot에 저장)
   */
  public CartResponse addItem(Long memberId, Long productId, Integer quantity) {
    validateQuantity(quantity);

    Cart cart = getOrCreateCart(memberId);

    // 상품이 없으면 404
    Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

    // 같은 cart + product 조합이 이미 있으면(uk_cart_item) 수량만 누적
    CartItem item = cartItemRepository
            .findByCart_IdAndProduct_IdAndDeletedAtIsNull(cart.getId(), product.getId())
            .orElse(null);

    if (item != null) {
      item.changeQuantity(item.getQuantity() + quantity);
      return getCart(memberId); // 변경 후 최신 장바구니를 다시 내려줌
    }

    // 새 아이템 추가(담는 시점 가격 스냅샷 저장)
    int currentPrice = resolveCurrentPrice(product);
    cartItemRepository.save(new CartItem(cart, product, quantity, currentPrice));
    return getCart(memberId);
  }

  /**
   * 장바구니 아이템 수량 변경.
   * - quantity 검증(>=1)
   * - cart_item 존재 여부 확인(+ deleted_at null)
   * - "내 장바구니의 아이템"인지 검증(다른 사람 아이템 수정 방지)
   */
  public CartResponse updateItemQuantity(Long memberId, Long cartItemId, Integer quantity) {
    validateQuantity(quantity);

    Cart cart = getOrCreateCart(memberId);

    CartItem item = cartItemRepository.findByIdAndDeletedAtIsNull(cartItemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found"));

    // 현재 회원의 cart_id와 item의 cart_id가 다르면 404로 숨김 처리(권한 유추 방지)
    if (!item.getCart().getId().equals(cart.getId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found");
    }

    item.changeQuantity(quantity);
    return getCart(memberId);
  }

  /**
   * 장바구니 아이템 삭제(논리삭제).
   * - cart_item 존재 여부 확인(+ deleted_at null)
   * - 내 장바구니 아이템인지 검증
   * - 실제 DELETE가 아니라 deleted_at을 채우는 soft delete
   */
  public void deleteItem(Long memberId, Long cartItemId) {
    Cart cart = getOrCreateCart(memberId);

    CartItem item = cartItemRepository.findByIdAndDeletedAtIsNull(cartItemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found"));

    if (!item.getCart().getId().equals(cart.getId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cart item not found");
    }

    item.softDelete();
  }

  /**
   * 수량 최소값 검증.
   * - Controller에서 @Valid로도 걸 수 있지만,
   *   서비스 레벨에서 한 번 더 방어해두면 안전함.
   */
  private void validateQuantity(Integer quantity) {
    if (quantity == null || quantity < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be >= 1");
    }
  }

  /**
   * Cart 생성 시 Member 엔티티를 FK로 물려야 하는데,
   * Member 전체를 조회(findById)까지 할 필요는 없음.
   *
   * - existsById로 "실제로 존재하는 회원인지"만 확인하고
   * - getReference(Member.class, id)로 프록시(참조)만 만들어서 Cart에 세팅
   *   => 불필요한 SELECT를 줄이는 패턴
   */
  private Member getMemberReference(Long memberId) {
    if (memberId == null || !memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }
    return entityManager.getReference(Member.class, memberId);
  }

  private int resolveCurrentPrice(Product product) {
    if (product == null) {
      return 0;
    }
    Integer livePrice = broadcastProductRepository.findLiveBpPriceByProductId(product.getId())
            .stream()
            .findFirst()
            .orElse(null);
    return livePrice != null ? livePrice : product.getPrice();
  }
}
