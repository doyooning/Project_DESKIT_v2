package com.deskit.deskit.order.payment.repository;

import com.deskit.deskit.order.payment.entity.TossRefund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TossRefundRepository extends JpaRepository<TossRefund, Long> {
  Optional<TossRefund> findByTossPaymentKey(String tossPaymentKey);
}
