package com.deskit.deskit.order.payment.repository;

import com.deskit.deskit.order.payment.entity.TossPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TossPaymentRepository extends JpaRepository<TossPayment, Long> {
  Optional<TossPayment> findByTossPaymentKey(String tossPaymentKey);
  Optional<TossPayment> findByOrderId(String orderId);
  Optional<TossPayment> findByTossOrderId(String tossOrderId);
}
