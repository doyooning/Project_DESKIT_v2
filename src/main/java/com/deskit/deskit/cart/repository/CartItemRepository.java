package com.deskit.deskit.cart.repository;

import com.deskit.deskit.cart.entity.CartItem;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * 특정 장바구니(cartId)에 속한 CartItem들을 전부 조회
     * - CartItem.cart.id = :cartId 조건
     * - deletedAt이 NULL인 것만 조회(논리삭제 제외)
     * - id 기준 오름차순 정렬
     *
     * findAllByCart_IdAndDeletedAtIsNullOrderByIdAsc
     * └ Cart(연관객체)의 id 필드로 조건을 걸고 + deletedAt NULL 조건 + id ASC 정렬
     */
    List<CartItem> findAllByCart_IdAndDeletedAtIsNullOrderByIdAsc(Long cartId);

    /**
     * 특정 장바구니(cartId) 안에서 특정 상품(productId)에 해당하는 CartItem 1개 조회
     * - CartItem.cart.id = :cartId
     * - CartItem.product.id = :productId
     * - deletedAt이 NULL인 것만 조회
     *
     * Optional을 쓰는 이유:
     * - 결과가 없을 수도 있기 때문에(null 대신 Optional로 안전하게 표현)
     *
     * 참고:
     * - DB에 UNIQUE (cart_id, product_id)가 걸려있어서,
     * 정상 데이터라면 "최대 1건"만 나오는게 맞음.
     */
    Optional<CartItem> findByCart_IdAndProduct_IdAndDeletedAtIsNull(Long cartId, Long productId);

    Optional<CartItem> findByIdAndDeletedAtIsNull(Long cartItemId);
}
