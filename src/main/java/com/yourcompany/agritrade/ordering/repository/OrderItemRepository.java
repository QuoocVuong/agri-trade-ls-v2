package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
  List<OrderItem> findByOrderId(Long orderId);
}
