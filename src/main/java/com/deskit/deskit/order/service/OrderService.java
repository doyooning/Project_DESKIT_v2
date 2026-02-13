package com.deskit.deskit.order.service;

import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.address.service.AddressService;
import com.deskit.deskit.order.dto.OrderCancelRequest;
import com.deskit.deskit.order.dto.OrderCancelResponse;
import com.deskit.deskit.order.dto.CreateOrderItemRequest;
import com.deskit.deskit.order.dto.CreateOrderRequest;
import com.deskit.deskit.order.dto.CreateOrderResponse;
import com.deskit.deskit.order.dto.OrderDetailResponse;
import com.deskit.deskit.order.dto.OrderItemResponse;
import com.deskit.deskit.order.dto.OrderSummaryResponse;
import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.entity.OrderItem;
import com.deskit.deskit.order.enums.OrderStatus;
import com.deskit.deskit.order.payment.service.TossPaymentService;
import com.deskit.deskit.order.repository.OrderItemRepository;
import com.deskit.deskit.order.repository.OrderRepository;
import com.deskit.deskit.livehost.repository.BroadcastProductRepository;
import com.deskit.deskit.livehost.service.BroadcastService;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final BroadcastProductRepository broadcastProductRepository;
  private final MemberRepository memberRepository;
  private final TossPaymentService tossPaymentService;
  private final BroadcastService broadcastService;
  private final AddressService addressService;

  public CreateOrderResponse createOrder(Long memberId, CreateOrderRequest request) {
    if (memberId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member_id required");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }

    List<CreateOrderItemRequest> items = request.items();
    if (items == null || items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items required");
    }

    String receiver = normalizeReceiver(request.receiver());
    String postcode = normalizePostcode(request.postcode());
    String addrDetail = normalizeAddrDetail(request.addrDetail());

    Map<Long, Integer> quantityByProductId = new HashMap<>();
    for (CreateOrderItemRequest item : items) {
      if (item == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item required");
      }
      Long productId = item.productId();
      if (productId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product_id required");
      }
      int quantity = safeQuantity(item.quantity());
      quantityByProductId.merge(productId, quantity, Integer::sum);
    }

    List<Long> productIds = new ArrayList<>(quantityByProductId.keySet());
    Collections.sort(productIds);

    Map<Long, Product> productsById = new HashMap<>();
    for (Long productId : productIds) {
      Product product = productRepository.findByIdForUpdateAndStatus(productId, Product.Status.ON_SALE)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
      int requestedQty = quantityByProductId.get(productId);
      Integer stockQty = product.getStockQty();
      int currentStock = stockQty == null ? 0 : stockQty;
      if (currentStock < requestedQty) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "insufficient stock: product_id=" + productId);
      }
      product.decreaseStock(requestedQty);
      broadcastService.restoreCostPriceIfSoldOut(productId);
      productsById.put(productId, product);
    }

    int totalProductAmount = 0;
    for (CreateOrderItemRequest item : items) {
      int quantity = safeQuantity(item.quantity());
      Product product = productsById.get(item.productId());
      int unitPrice = resolveCurrentPrice(product);
      totalProductAmount += unitPrice * quantity;
    }

    int shippingFee = totalProductAmount >= 50000 ? 0 : 3000;
    int discountFee = 0;
    int orderAmount = totalProductAmount - discountFee + shippingFee;
    String orderNumber = generateOrderNumber();

    Order order = Order.create(
      memberId,
      addrDetail,
      orderNumber,
      totalProductAmount,
      shippingFee,
      discountFee,
      orderAmount,
      OrderStatus.CREATED
    );
    Order savedOrder = orderRepository.save(order);
    addressService.saveAddressFromOrder(memberId, receiver, postcode, addrDetail, request.isDefault());

    for (CreateOrderItemRequest item : items) {
      int quantity = safeQuantity(item.quantity());
      Product product = productsById.get(item.productId());
      int unitPrice = resolveCurrentPrice(product);
      int subtotal = unitPrice * quantity;
      OrderItem orderItem = OrderItem.create(
        savedOrder,
        product.getId(),
        product.getSellerId(),
        product.getProductName(),
        unitPrice,
        quantity,
        subtotal
      );
      orderItemRepository.save(orderItem);
    }

    return new CreateOrderResponse(
      savedOrder.getId(),
      savedOrder.getOrderNumber(),
      savedOrder.getStatus(),
      savedOrder.getOrderAmount()
    );
  }

  int resolveCurrentPrice(Product product) {
    if (product == null) {
      return 0;
    }
    Integer livePrice = broadcastProductRepository.findLiveBpPriceByProductId(product.getId())
      .stream()
      .findFirst()
      .orElse(null);
    return livePrice != null ? livePrice : product.getPrice();
  }

  @Transactional(readOnly = true)
  public List<OrderSummaryResponse> getMyOrders(Long memberId) {
    if (memberId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member_id required");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    return orderRepository.findByMemberIdAndDeletedAtIsNullOrderByCreatedAtDesc(memberId)
      .stream()
      .map(OrderSummaryResponse::from)
      .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public OrderDetailResponse getMyOrderDetail(Long memberId, Long orderId) {
    if (memberId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member_id required");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }
    if (orderId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order_id required");
    }

    Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    if (!order.getMemberId().equals(memberId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    List<OrderItemResponse> items = orderItemRepository.findByOrder_Id(orderId)
      .stream()
      .map(OrderItemResponse::from)
      .collect(Collectors.toList());

    return OrderDetailResponse.from(order, items);
  }

  public void abandonCreatedOrder(Long memberId, Long orderId) {
    if (memberId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member_id required");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }
    if (orderId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order_id required");
    }

    Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    if (!order.getMemberId().equals(memberId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    if (order.getStatus() != OrderStatus.CREATED) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    int updated = orderRepository.markCreatedOrderDeleted(orderId, memberId, now);
    if (updated > 0) {
      orderItemRepository.markDeletedByOrderId(orderId, now);
    }
  }

  public OrderCancelResponse requestCancel(Long memberId, Long orderId, OrderCancelRequest request) {
    if (memberId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member_id required");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }
    if (orderId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order_id required");
    }
    if (request == null || request.reason() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason required");
    }
    String reason = request.reason().trim();
    if (reason.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason required");
    }

    Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    if (!order.getMemberId().equals(memberId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    if (isFinalizedCancelState(order.getStatus())) {
      return new OrderCancelResponse(order.getId(), order.getStatus());
    }

    if (order.getStatus() == OrderStatus.CREATED) {
      int updated = orderRepository.cancelCreatedOrder(orderId, memberId, reason, LocalDateTime.now());
      Order latest = loadOwnedOrder(memberId, orderId);
      if (updated == 0 && !isFinalizedCancelState(latest.getStatus())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "cancel state changed");
      }
      return new OrderCancelResponse(latest.getId(), latest.getStatus());
    }

    if (order.getStatus() == OrderStatus.PAID) {
      int updated = orderRepository.requestRefundForPaidOrder(orderId, memberId, reason);
      Order latest = loadOwnedOrder(memberId, orderId);
      if (updated == 0 && latest.getStatus() != OrderStatus.REFUND_REQUESTED && latest.getStatus() != OrderStatus.REFUNDED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "cancel state changed");
      }

      if (latest.getStatus() == OrderStatus.REFUNDED) {
        return new OrderCancelResponse(latest.getId(), latest.getStatus());
      }
      order = latest;
    } else if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status for cancel request");
    }

    try {
      tossPaymentService.cancelPayment(order, reason);
    } catch (ResponseStatusException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "toss cancel failed", ex);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "toss cancel failed", ex);
    }

    orderRepository.approveRefundRequest(orderId, memberId, LocalDateTime.now());
    Order afterRefund = loadOwnedOrder(memberId, orderId);
    if (afterRefund.getStatus() == OrderStatus.REFUNDED) {
      updateBroadcastSalesAfterRefund(afterRefund);
    }
    return new OrderCancelResponse(afterRefund.getId(), afterRefund.getStatus());
  }

  private Order loadOwnedOrder(Long memberId, Long orderId) {
    Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    if (!order.getMemberId().equals(memberId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }
    return order;
  }

  private boolean isFinalizedCancelState(OrderStatus status) {
    return status == OrderStatus.CANCELLED
            || status == OrderStatus.REFUNDED;
  }

  private void updateBroadcastSalesAfterRefund(Order order) {
    if (order == null) {
      return;
    }
    LocalDateTime paidAt = order.getPaidAt();
    if (paidAt == null) {
      return;
    }
    List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
    if (items.isEmpty()) {
      return;
    }
    List<Long> productIds = items.stream()
            .map(OrderItem::getProductId)
            .distinct()
            .toList();
    if (productIds.isEmpty()) {
      return;
    }
    List<Long> broadcastIds = broadcastProductRepository.findBroadcastIdsByProductIdsAndPaidAt(productIds, paidAt);
    for (Long broadcastId : broadcastIds) {
      broadcastService.refreshBroadcastTotalSales(broadcastId);
    }
  }

  private int safeQuantity(Integer quantity) {
    if (quantity == null || quantity < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be >= 1");
    }
    return quantity;
  }

  private String normalizeReceiver(String receiver) {
    String normalized = receiver == null ? "" : receiver.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiver required");
    }
    return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
  }

  private String normalizePostcode(String postcode) {
    String normalized = postcode == null ? "" : postcode.trim();
    if (!normalized.matches("\\d{5}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "postcode invalid");
    }
    return normalized;
  }

  private String normalizeAddrDetail(String addrDetail) {
    String normalized = addrDetail == null ? "" : addrDetail.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr_detail required");
    }
    return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
  }

  private String generateOrderNumber() {
    long now = System.currentTimeMillis();
    int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
    return "ORD-" + now + "-" + suffix;
  }
}
