package com.deskit.deskit.cart.repository;

import com.deskit.deskit.cart.entity.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

  /**
   * 특정 회원(memberId)의 장바구니를 조회한다.
   * - Cart.member.memberId = :memberId 조건
   * - deletedAt이 NULL인 것만 조회(논리삭제 제외)
   *
   * findByMember_MemberIdAndDeletedAtIsNull
   *  └ member(연관객체) 안의 memberId 필드로 조건 + deletedAt NULL 조건
   *
   * Optional을 쓰는 이유:
   * - 장바구니가 아직 생성되지 않았을 수도 있어서 "없음" 상태를 안전하게 표현하려고.
   *
   * 참고:
   * - DB에서 cart 테이블은 member_id에 UNIQUE 제약이 있어서(회원당 1개)
   *   정상 데이터라면 결과는 최대 1개가 맞다.
   */
  Optional<Cart> findByMember_MemberIdAndDeletedAtIsNull(Long memberId);
}