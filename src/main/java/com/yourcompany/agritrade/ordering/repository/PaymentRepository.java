package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByTransactionCode(String transactionCode);
}