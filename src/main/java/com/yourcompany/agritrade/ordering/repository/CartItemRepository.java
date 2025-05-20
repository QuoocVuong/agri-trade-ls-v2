package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

  List<CartItem> findByUserId(Long userId);

  Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

  // Xóa tất cả item của user (ví dụ: sau khi checkout)
  void deleteByUserId(Long userId);

  // Xóa item cụ thể
  void deleteByUserIdAndProductId(Long userId, Long productId);

  // Cập nhật số lượng (ví dụ)
  @Modifying // Cần thiết cho các câu lệnh UPDATE/DELETE tùy chỉnh
  @Query(
      "UPDATE CartItem c SET c.quantity = :quantity WHERE c.id = :cartItemId AND c.user.id = :userId")
  int updateQuantityForUser(
      @Param("userId") Long userId,
      @Param("cartItemId") Long cartItemId,
      @Param("quantity") int quantity);
}
