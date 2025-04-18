package com.yourcompany.agritradels.catalog.repository;

import com.yourcompany.agritradels.catalog.domain.ProductPricingTier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductPricingTierRepository extends JpaRepository<ProductPricingTier, Long> {
    List<ProductPricingTier> findByProductIdOrderByMinQuantityAsc(Long productId); // Lấy theo thứ tự số lượng
    void deleteByProductId(Long productId); // Xóa các bậc giá của sản phẩm
}