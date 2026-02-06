package com.deskit.deskit.order.service;

import com.deskit.deskit.order.dto.OrderItemResponse;
import com.deskit.deskit.order.dto.SellerOrderDetailResponse;
import com.deskit.deskit.order.dto.SellerOrderSummaryResponse;
import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.entity.OrderItem;
import com.deskit.deskit.order.enums.OrderStatus;
import com.deskit.deskit.order.repository.OrderItemRepository;
import com.deskit.deskit.order.repository.OrderRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SellerOrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;

  public SellerOrderService(
    OrderRepository orderRepository,
    OrderItemRepository orderItemRepository
  ) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
  }

  public Page<SellerOrderSummaryResponse> getSellerOrders(
    Long sellerId,
    OrderStatus status,
    Pageable pageable
  ) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seller_id required");
    }

    Page<Order> orders = orderRepository.findSellerOrders(sellerId, status, pageable);
    List<Order> content = orders.getContent();
    if (content.isEmpty()) {
      return orders.map(order -> SellerOrderSummaryResponse.from(order, 0, null));
    }

    List<Long> orderIds = content.stream()
      .map(Order::getId)
      .filter(id -> id != null)
      .toList();
    if (orderIds.isEmpty()) {
      return orders.map(order -> SellerOrderSummaryResponse.from(order, 0, null));
    }

    List<OrderItem> items =
      orderItemRepository.findByOrder_IdInAndSellerIdAndDeletedAtIsNullOrderByIdAsc(orderIds, sellerId);
    Map<Long, List<OrderItem>> itemsByOrderId = new HashMap<>();
    for (OrderItem item : items) {
      Long orderId = item.getOrder() == null ? null : item.getOrder().getId();
      if (orderId == null) {
        continue;
      }
      itemsByOrderId.computeIfAbsent(orderId, key -> new java.util.ArrayList<>()).add(item);
    }

    return orders.map(order -> {
      List<OrderItem> orderItems = itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
      int itemCount = orderItems.size();
      String firstProductName = orderItems.isEmpty() ? null : orderItems.get(0).getProductName();
      return SellerOrderSummaryResponse.from(order, itemCount, firstProductName);
    });
  }

  public SellerOrderDetailResponse getSellerOrderDetail(Long sellerId, Long orderId) {
    if (sellerId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seller_id required");
    }
    if (orderId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order_id required");
    }

    if (!orderItemRepository.existsByOrder_IdAndSellerIdAndDeletedAtIsNull(orderId, sellerId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
    }

    Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

    List<OrderItemResponse> items = orderItemRepository
      .findByOrder_IdAndSellerIdAndDeletedAtIsNull(orderId, sellerId)
      .stream()
      .map(OrderItemResponse::from)
      .toList();

    return SellerOrderDetailResponse.from(order, items);
  }
}
