package com.yourcompany.agritrade.catalog.repository;

import com.yourcompany.agritrade.catalog.domain.ProductPricingTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPricingTierRepository extends JpaRepository<ProductPricingTier, Long> {
  List<ProductPricingTier> findByProductIdOrderByMinQuantityAsc(
      Long productId); // Lấy theo thứ tự số lượng

  void deleteByProductId(Long productId); // Xóa các bậc giá của sản phẩm
}
