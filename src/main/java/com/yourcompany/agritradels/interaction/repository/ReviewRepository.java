package com.yourcompany.agritradels.interaction.repository;

import com.yourcompany.agritradels.common.model.ReviewStatus; // Import Enum
import com.yourcompany.agritradels.interaction.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // Import nếu cần lọc động
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> { // Thêm Spec Executor

    // Tìm review theo sản phẩm (chỉ lấy review đã duyệt, phân trang)
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    // Tìm review theo người dùng (phân trang)
    Page<Review> findByConsumerId(Long consumerId, Pageable pageable);

    // Kiểm tra xem user đã review sản phẩm này trong đơn hàng cụ thể chưa
    boolean existsByConsumerIdAndProductIdAndOrderId(Long consumerId, Long productId, Long orderId);

    // Kiểm tra xem user đã review sản phẩm này chưa (bất kể đơn hàng)
    boolean existsByConsumerIdAndProductId(Long consumerId, Long productId);

    // Lấy review theo ID và Consumer ID (để kiểm tra quyền sửa/xóa nếu cho phép)
    Optional<Review> findByIdAndConsumerId(Long id, Long consumerId);

    // Tính rating trung bình và số lượng review đã duyệt cho sản phẩm
    @Query("SELECT AVG(r.rating), COUNT(r.id) FROM Review r WHERE r.product.id = :productId AND r.status = :status")
    Optional<Object[]> getAverageRatingAndCountByProductIdAndStatus(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    // Tìm review theo trạng thái (cho Admin duyệt, phân trang)
    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);



    // Đếm review theo trạng thái (cho Admin)
    long countByStatus(ReviewStatus status);

    // Đếm review mới cho sản phẩm của farmer (chưa duyệt)
    @Query("SELECT COUNT(r.id) FROM Review r WHERE r.product.farmer.id = :farmerId AND r.status = :status")
    long countByProductFarmerIdAndStatus(@Param("farmerId") Long farmerId, @Param("status") ReviewStatus status);


    // Lấy các review mới nhất (pending)
    List<Review> findTopNByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);
}