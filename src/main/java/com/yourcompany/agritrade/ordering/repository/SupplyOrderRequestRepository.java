package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequest;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplyOrderRequestRepository
    extends JpaRepository<SupplyOrderRequest, Long>, JpaSpecificationExecutor<SupplyOrderRequest> {
  Page<SupplyOrderRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

  Page<SupplyOrderRequest> findByFarmerIdOrderByCreatedAtDesc(Long farmerId, Pageable pageable);

  List<SupplyOrderRequest> findByFarmerIdAndBuyerIdAndProductIdAndStatus(
      Long farmerId, Long buyerId, Long productId, SupplyOrderRequestStatus status);

  Long countByFarmerIdAndStatus(Long farmerId, SupplyOrderRequestStatus status);
}
