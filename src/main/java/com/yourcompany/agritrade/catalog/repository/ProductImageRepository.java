package com.yourcompany.agritrade.catalog.repository;

import com.yourcompany.agritrade.catalog.domain.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
  List<ProductImage> findByProductId(Long productId);

  void deleteByProductId(Long productId); // Xóa tất cả ảnh của sản phẩm
}
