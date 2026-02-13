package com.deskit.deskit.order.repository;

import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Order(주문) 엔티티에 대한 조회/저장 Repository.
 *
 * - JpaRepository가 기본 CRUD를 제공한다. (save, findById, delete 등)
 * - 아래 커스텀 메서드는 Spring Data JPA 메서드 네이밍 규칙으로 쿼리를 자동 생성한다.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

  /**
   * 특정 회원(member_id)의 주문 목록을 생성일(created_at) 기준 내림차순으로 조회한다.
   *
   * - findByMemberId...:
   *   Order 엔티티의 memberId 컬럼을 조건으로 필터링한다.
   *
   * - OrderByCreatedAtDesc:
   *   BaseEntity에 있는 createdAt(created_at) 컬럼 기준으로 최신 주문이 먼저 오도록 정렬한다.
   *
   * - 사용 예:
   *   List<Order> orders = orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
   */
  List<Order> findByMemberIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.orderNumber = :orderNumber")
  Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

  Optional<Order> findByIdAndDeletedAtIsNull(Long id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Order o
      set o.status = com.deskit.deskit.order.enums.OrderStatus.CANCELLED,
          o.cancelReason = :reason,
          o.cancelledAt = :now
      where o.id = :orderId
        and o.memberId = :memberId
        and o.deletedAt is null
        and o.status = com.deskit.deskit.order.enums.OrderStatus.CREATED
      """)
  int cancelCreatedOrder(
          @Param("orderId") Long orderId,
          @Param("memberId") Long memberId,
          @Param("reason") String reason,
          @Param("now") LocalDateTime now
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Order o
      set o.status = com.deskit.deskit.order.enums.OrderStatus.REFUND_REQUESTED,
          o.cancelReason = :reason
      where o.id = :orderId
        and o.memberId = :memberId
        and o.deletedAt is null
        and o.status = com.deskit.deskit.order.enums.OrderStatus.PAID
      """)
  int requestRefundForPaidOrder(
          @Param("orderId") Long orderId,
          @Param("memberId") Long memberId,
          @Param("reason") String reason
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Order o
      set o.status = com.deskit.deskit.order.enums.OrderStatus.REFUNDED,
          o.refundedAt = :now
      where o.id = :orderId
        and o.memberId = :memberId
        and o.deletedAt is null
        and o.status = com.deskit.deskit.order.enums.OrderStatus.REFUND_REQUESTED
      """)
  int approveRefundRequest(
          @Param("orderId") Long orderId,
          @Param("memberId") Long memberId,
          @Param("now") LocalDateTime now
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Order o
      set o.deletedAt = :now
      where o.id = :orderId
        and o.memberId = :memberId
        and o.deletedAt is null
        and o.status = com.deskit.deskit.order.enums.OrderStatus.CREATED
      """)
  int markCreatedOrderDeleted(
          @Param("orderId") Long orderId,
          @Param("memberId") Long memberId,
          @Param("now") LocalDateTime now
  );

  @Query(
    value = """
      select distinct o
      from Order o
      join OrderItem oi on oi.order = o
      where o.deletedAt is null
        and oi.deletedAt is null
        and oi.sellerId = :sellerId
        and (:status is null or o.status = :status)
      """,
    countQuery = """
      select count(distinct o.id)
      from Order o
      join OrderItem oi on oi.order = o
      where o.deletedAt is null
        and oi.deletedAt is null
        and oi.sellerId = :sellerId
        and (:status is null or o.status = :status)
      """
  )
  Page<Order> findSellerOrders(@Param("sellerId") Long sellerId,
                               @Param("status") OrderStatus status,
                               Pageable pageable);
}
