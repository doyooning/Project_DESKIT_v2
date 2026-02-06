package com.deskit.deskit.order.payment.service;

import com.deskit.deskit.order.entity.Order;
import com.deskit.deskit.order.enums.OrderStatus;
import com.deskit.deskit.order.payment.dto.TossPaymentConfirmRequest;
import com.deskit.deskit.order.payment.dto.TossPaymentConfirmResult;
import com.deskit.deskit.order.payment.entity.TossPayment;
import com.deskit.deskit.order.payment.entity.TossRefund;
import com.deskit.deskit.order.payment.repository.TossPaymentRepository;
import com.deskit.deskit.order.payment.repository.TossRefundRepository;
import com.deskit.deskit.order.repository.OrderItemRepository;
import com.deskit.deskit.order.repository.OrderRepository;
import com.deskit.deskit.livehost.repository.BroadcastProductRepository;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.repository.ProductRepository;
import com.deskit.deskit.order.entity.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TossPaymentService {

  private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
  private static final String CANCEL_URL_TEMPLATE = "https://api.tosspayments.com/v1/payments/%s/cancel";

  private final OrderRepository orderRepository;
  private final TossPaymentRepository tossPaymentRepository;
  private final TossRefundRepository tossRefundRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final BroadcastProductRepository broadcastProductRepository;
  private final ObjectMapper objectMapper;

  @Value("${toss.payments.secret-key}")
  private String tossSecretKey;

  public TossPaymentService(
    OrderRepository orderRepository,
    TossPaymentRepository tossPaymentRepository,
    TossRefundRepository tossRefundRepository,
    OrderItemRepository orderItemRepository,
    ProductRepository productRepository,
    BroadcastProductRepository broadcastProductRepository,
    ObjectMapper objectMapper
  ) {
    this.orderRepository = orderRepository;
    this.tossPaymentRepository = tossPaymentRepository;
    this.tossRefundRepository = tossRefundRepository;
    this.orderItemRepository = orderItemRepository;
    this.productRepository = productRepository;
    this.broadcastProductRepository = broadcastProductRepository;
    this.objectMapper = objectMapper;
  }

  public TossPaymentConfirmResult confirmPayment(TossPaymentConfirmRequest request) {
    String paymentKey = normalizeText(request.paymentKey());
    String orderIdText = normalizeText(request.orderId());
    Long amount = request.amount();

    if (tossSecretKey == null || tossSecretKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "missing toss secret key");
    }
    if (paymentKey.isEmpty() || orderIdText.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid payment request");
    }
    if (amount == null || amount < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount");
    }

    Order order = findOrderForUpdate(orderIdText);

    Integer orderAmount = order.getOrderAmount();
    long expectedAmount = orderAmount == null ? 0L : orderAmount;
    if (expectedAmount != amount) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount mismatch");
    }
    if (!isOrderAmountCurrent(order)) {
      cancelOrderDueToPriceChange(order);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "order amount changed");
    }

    Optional<TossPayment> existing = tossPaymentRepository.findByTossPaymentKey(paymentKey);
    if (existing.isPresent()) {
      updateOrderPaid(order);
      return new TossPaymentConfirmResult(
        HttpStatus.OK.value(),
        buildResponseFrom(existing.get())
      );
    }

    Map<String, Object> body = new HashMap<>();
    body.put("paymentKey", paymentKey);
    body.put("orderId", orderIdText);
    body.put("amount", amount);

    String idempotencyKey = generateIdempotencyKey(paymentKey, orderIdText, amount);

    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(CONFIRM_URL).openConnection();
      connection.setRequestProperty("Authorization", basicAuthHeader(tossSecretKey));
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Idempotency-Key", idempotencyKey);
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);

      try (OutputStream outputStream = connection.getOutputStream()) {
        objectMapper.writeValue(outputStream, body);
      }

      int statusCode = connection.getResponseCode();
      boolean isSuccess = statusCode == HttpURLConnection.HTTP_OK;
      try (InputStream responseStream = isSuccess
        ? connection.getInputStream()
        : connection.getErrorStream()) {
        Map<String, Object> responseBody = objectMapper.readValue(
          responseStream,
          new TypeReference<>() {}
        );

        if (isSuccess) {
          TossPayment payment = toEntity(responseBody, orderIdText);
          tossPaymentRepository.save(payment);
          updateOrderPaid(order);
        }

        return new TossPaymentConfirmResult(statusCode, responseBody);
      }
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "toss confirm failed", ex);
    }
  }

  public void cancelPayment(Order order, String reason) {
    if (order == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order required");
    }
    if (tossSecretKey == null || tossSecretKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "missing toss secret key");
    }

    TossPayment payment = findPaymentForOrder(order);
    if (payment == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found");
    }

    if (isAlreadyCanceled(payment)) {
      return;
    }

    Long cancelAmount = resolveCancelAmount(order, payment);
    if (cancelAmount == null || cancelAmount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cancel amount");
    }
    Map<String, Object> body = new HashMap<>();
    body.put("cancelReason", normalizeReason(reason));
    body.put("cancelAmount", cancelAmount);

    String idempotencyKey = generateCancelIdempotencyKey(
      payment.getTossPaymentKey(),
      payment.getOrderId(),
      cancelAmount
    );

    try {
      String cancelUrl = String.format(CANCEL_URL_TEMPLATE, payment.getTossPaymentKey());
      HttpURLConnection connection = (HttpURLConnection) new URL(cancelUrl).openConnection();
      connection.setRequestProperty("Authorization", basicAuthHeader(tossSecretKey));
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Idempotency-Key", idempotencyKey);
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);

      try (OutputStream outputStream = connection.getOutputStream()) {
        objectMapper.writeValue(outputStream, body);
      }

      int statusCode = connection.getResponseCode();
      boolean isSuccess = statusCode == HttpURLConnection.HTTP_OK;
      try (InputStream responseStream = isSuccess
        ? connection.getInputStream()
        : connection.getErrorStream()) {
        Map<String, Object> responseBody = objectMapper.readValue(
          responseStream,
          new TypeReference<>() {}
        );

        if (!isSuccess) {
          throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "toss cancel failed");
        }

        updatePaymentCanceled(payment, responseBody);
        saveRefundIfNeeded(payment, responseBody, cancelAmount, reason);
      }
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "toss cancel failed", ex);
    }
  }

  private void updateOrderPaid(Order order) {
    if (order.getStatus() == OrderStatus.PAID) {
      return;
    }
    if (order.getStatus() != OrderStatus.CREATED) {
      return;
    }
    order.markPaid();
  }

  private boolean isOrderAmountCurrent(Order order) {
    List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
    if (items.isEmpty()) {
      return false;
    }
    int totalProductAmount = 0;
    for (OrderItem item : items) {
      Long productId = item.getProductId();
      if (productId == null) {
        return false;
      }
      Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
        .orElse(null);
      if (product == null) {
        return false;
      }
      int unitPrice = resolveCurrentPrice(product);
      totalProductAmount += unitPrice * item.getQuantity();
    }
    int shippingFee = totalProductAmount >= 50000 ? 0 : 3000;
    int discountFee = order.getDiscountFee() == null ? 0 : order.getDiscountFee();
    int recalculatedAmount = totalProductAmount - discountFee + shippingFee;
    Integer orderAmount = order.getOrderAmount();
    return orderAmount != null && orderAmount == recalculatedAmount;
  }

  private int resolveCurrentPrice(Product product) {
    Integer livePrice = broadcastProductRepository.findLiveBpPriceByProductId(product.getId())
      .stream()
      .findFirst()
      .orElse(null);
    return livePrice != null ? livePrice : product.getPrice();
  }

  private void cancelOrderDueToPriceChange(Order order) {
    if (order.getStatus() != OrderStatus.CREATED) {
      return;
    }
    order.requestCancel("price changed");
    order.approveCancel();
    orderRepository.save(order);
  }

  private Order findOrderForUpdate(String orderIdText) {
    if (orderIdText.matches("^[0-9]+$")) {
      try {
        Long id = Long.parseLong(orderIdText);
        Optional<Order> byId = orderRepository.findByIdForUpdate(id);
        if (byId.isPresent()) {
          return byId.get();
        }
      } catch (NumberFormatException ignored) {
      }
    }

    return orderRepository.findByOrderNumberForUpdate(orderIdText)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
  }

  private TossPayment findPaymentForOrder(Order order) {
    String idText = order.getId() == null ? null : String.valueOf(order.getId());
    if (idText != null) {
      Optional<TossPayment> byOrderId = tossPaymentRepository.findByOrderId(idText);
      if (byOrderId.isPresent()) {
        return byOrderId.get();
      }
      Optional<TossPayment> byTossOrderId = tossPaymentRepository.findByTossOrderId(idText);
      if (byTossOrderId.isPresent()) {
        return byTossOrderId.get();
      }
    }
    String orderNumber = order.getOrderNumber();
    if (orderNumber != null && !orderNumber.isBlank()) {
      Optional<TossPayment> byOrderNumber = tossPaymentRepository.findByOrderId(orderNumber);
      if (byOrderNumber.isPresent()) {
        return byOrderNumber.get();
      }
      Optional<TossPayment> byTossOrderNumber = tossPaymentRepository.findByTossOrderId(orderNumber);
      if (byTossOrderNumber.isPresent()) {
        return byTossOrderNumber.get();
      }
    }
    return null;
  }

  private boolean isAlreadyCanceled(TossPayment payment) {
    String status = payment.getStatus();
    if (status == null) {
      return false;
    }
    return status.equalsIgnoreCase("CANCELED") || status.equalsIgnoreCase("PARTIAL_CANCELED");
  }

  private Long resolveCancelAmount(Order order, TossPayment payment) {
    Integer orderAmount = order.getOrderAmount();
    if (orderAmount != null && orderAmount > 0) {
      return orderAmount.longValue();
    }
    Long totalAmount = payment.getTotalAmount();
    return totalAmount == null ? 0L : totalAmount;
  }

  private String normalizeReason(String reason) {
    if (reason == null) {
      return "customer request";
    }
    String trimmed = reason.trim();
    return trimmed.isEmpty() ? "customer request" : trimmed;
  }

  private String generateCancelIdempotencyKey(String paymentKey, String orderId, Long amount) {
    String baseOrderId = orderId == null ? "" : orderId;
    long safeAmount = amount == null ? 0L : amount;
    return generateIdempotencyKey(paymentKey, baseOrderId + ":cancel", safeAmount);
  }

  private void updatePaymentCanceled(TossPayment payment, Map<String, Object> responseBody) {
    String status = asText(responseBody.get("status"));
    if (status != null && !status.isBlank()) {
      payment.updateStatus(status);
      tossPaymentRepository.save(payment);
    }
  }

  private void saveRefundIfNeeded(
    TossPayment payment,
    Map<String, Object> responseBody,
    Long cancelAmount,
    String reason
  ) {
    if (tossRefundRepository.findByTossPaymentKey(payment.getTossPaymentKey()).isPresent()) {
      return;
    }

    Map<String, Object> cancelInfo = extractFirstCancel(responseBody);
    String refundKey = cancelInfo == null ? null : asText(cancelInfo.get("cancelRequestId"));
    if (refundKey == null || refundKey.isBlank()) {
      refundKey = cancelInfo == null ? null : asText(cancelInfo.get("transactionKey"));
    }
    if (refundKey == null || refundKey.isBlank()) {
      refundKey = payment.getTossPaymentKey() + ":" + System.currentTimeMillis();
    }

    Long refundAmount = cancelInfo == null ? null : asLong(cancelInfo.get("cancelAmount"));
    if (refundAmount == null) {
      refundAmount = cancelAmount == null ? 0L : cancelAmount;
    }

    String refundReason = cancelInfo == null ? null : asText(cancelInfo.get("cancelReason"));
    if (refundReason == null || refundReason.isBlank()) {
      refundReason = normalizeReason(reason);
    }

    String refundStatus = cancelInfo == null ? null : asText(cancelInfo.get("cancelStatus"));
    if (refundStatus == null || refundStatus.isBlank()) {
      refundStatus = "DONE";
    }

    LocalDateTime canceledAt = cancelInfo == null ? null : parseDate(asText(cancelInfo.get("canceledAt")));
    LocalDateTime requestedAt = canceledAt == null ? LocalDateTime.now() : canceledAt;
    LocalDateTime approvedAt = canceledAt;

    TossRefund refund =
      TossRefund.create(
        refundKey,
        refundAmount,
        refundReason,
        refundStatus,
        requestedAt,
        approvedAt,
        payment.getId(),
        payment.getTossPaymentKey()
      );
    tossRefundRepository.save(refund);
  }

  private Map<String, Object> extractFirstCancel(Map<String, Object> responseBody) {
    if (responseBody == null) {
      return null;
    }
    Object cancelsValue = responseBody.get("cancels");
    if (!(cancelsValue instanceof Iterable<?> cancels)) {
      return null;
    }
    for (Object item : cancels) {
      if (item instanceof Map<?, ?> rawMap) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
          if (entry.getKey() != null) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        return result;
      }
      break;
    }
    return null;
  }

  private TossPayment toEntity(Map<String, Object> responseBody, String orderIdText) {
    String paymentKey = asText(responseBody.get("paymentKey"));
    String tossOrderId = asText(responseBody.get("orderId"));
    String method = mapMethod(asText(responseBody.get("method")));
    String status = asText(responseBody.get("status"));
    Long totalAmount = asLong(responseBody.get("totalAmount"));
    LocalDateTime requestedAt = parseDate(asText(responseBody.get("requestedAt")));
    LocalDateTime approvedAt = parseDate(asText(responseBody.get("approvedAt")));

    if (paymentKey == null || paymentKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "missing payment key");
    }

    return TossPayment.create(
      paymentKey,
      tossOrderId == null || tossOrderId.isBlank() ? orderIdText : tossOrderId,
      method,
      status,
      requestedAt != null ? requestedAt : LocalDateTime.now(),
      approvedAt,
      totalAmount == null ? 0L : totalAmount,
      orderIdText
    );
  }

  private String mapMethod(String method) {
    if (method == null) {
      return "신용/체크카드";
    }
    if (method.contains("계좌")) {
      return "계좌이체";
    }
    if (method.contains("카드")) {
      return "신용/체크카드";
    }
    return "신용/체크카드";
  }

  private LocalDateTime parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return OffsetDateTime.parse(value).toLocalDateTime();
  }

  private Map<String, Object> buildResponseFrom(TossPayment payment) {
    Map<String, Object> response = new HashMap<>();
    response.put("paymentKey", payment.getTossPaymentKey());
    response.put("orderId", payment.getTossOrderId());
    response.put("status", payment.getStatus());
    response.put("totalAmount", payment.getTotalAmount());
    response.put("method", payment.getTossPaymentMethod());
    response.put("requestedAt", payment.getRequestDate());
    response.put("approvedAt", payment.getApprovedDate());
    return response;
  }

  private String basicAuthHeader(String secretKey) {
    String raw = secretKey + ":";
    String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  private String generateIdempotencyKey(String paymentKey, String orderId, Long amount) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String source = paymentKey + ":" + orderId + ":" + amount;
      byte[] hashed = digest.digest(source.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashed);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "idempotency failed", ex);
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }

  private String asText(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Long asLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
