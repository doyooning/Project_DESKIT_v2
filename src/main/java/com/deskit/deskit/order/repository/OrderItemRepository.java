package com.deskit.deskit.order.repository;

import com.deskit.deskit.order.entity.OrderItem;
import com.deskit.deskit.order.enums.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * OrderItem(주문 상품/라인 아이템) 조회/저장을 담당하는 Repository
 *
 * - Spring Data JPA가 메서드 이름 규칙을 해석해서 쿼리를 자동 생성한다.
 * - 기본 CRUD는 JpaRepository가 제공한다. (save, findById, delete 등)
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

  /**
   * 특정 주문(order_id)에 속한 주문 상품 목록을 조회한다.
   *
   * - findByOrder_Id:
   *   - OrderItem의 연관 필드인 "order"의
   *   - 그 안의 "id" 값을 조건으로 검색한다는 뜻 (order.id)
   */
  List<OrderItem> findByOrder_Id(Long orderId);

  List<OrderItem> findByOrder_IdAndSellerIdAndDeletedAtIsNull(Long orderId, Long sellerId);

  List<OrderItem> findByOrder_IdInAndSellerIdAndDeletedAtIsNullOrderByIdAsc(List<Long> orderIds, Long sellerId);

  boolean existsByOrder_IdAndSellerIdAndDeletedAtIsNull(Long orderId, Long sellerId);

  @Query("""
      select (count(oi) > 0)
      from OrderItem oi
      join oi.order o
      where oi.productId = :productId
        and oi.deletedAt is null
        and o.deletedAt is null
        and o.status in :statuses
      """)
  boolean existsPaidOrderByProductId(@Param("productId") Long productId,
                                     @Param("statuses") List<OrderStatus> statuses);
}
