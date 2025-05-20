package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  List<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByTransactionCode(String transactionCode);
}
