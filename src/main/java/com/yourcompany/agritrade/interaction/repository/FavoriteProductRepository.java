package com.yourcompany.agritrade.interaction.repository;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.interaction.domain.FavoriteProduct;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteProductRepository
    extends JpaRepository<FavoriteProduct, FavoriteProduct.FavoriteProductId> {

  // Kiểm tra xem user đã yêu thích sản phẩm này chưa
  boolean existsByUserIdAndProductId(Long userId, Long productId);

  // Tìm bản ghi yêu thích cụ thể
  Optional<FavoriteProduct> findByUserIdAndProductId(Long userId, Long productId);

  // Lấy danh sách sản phẩm yêu thích của user (phân trang)
  @Query("SELECT fp.product FROM FavoriteProduct fp WHERE fp.user.id = :userId")
  Page<Product> findFavoriteProductsByUserId(@Param("userId") Long userId, Pageable pageable);

  // Đếm số lượng sản phẩm yêu thích của user
  long countByUserId(Long userId);

  // Đếm số lượt yêu thích của một sản phẩm
  long countByProductId(Long productId);

  // Xóa bản ghi yêu thích
  void deleteByUserIdAndProductId(Long userId, Long productId);
}
