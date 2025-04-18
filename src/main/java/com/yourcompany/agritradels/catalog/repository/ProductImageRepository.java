package com.yourcompany.agritradels.catalog.repository;

import com.yourcompany.agritradels.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductId(Long productId);
    void deleteByProductId(Long productId); // Xóa tất cả ảnh của sản phẩm
}