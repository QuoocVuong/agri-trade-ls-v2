package com.yourcompany.agritrade.interaction.service;

import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface ReviewService {

  /** Tạo review mới cho sản phẩm (user phải mua sản phẩm này rồi - tùy chọn kiểm tra) */
  ReviewResponse createReview(Authentication authentication, ReviewRequest request);

  /** Lấy danh sách review đã duyệt của một sản phẩm (phân trang) */
  Page<ReviewResponse> getApprovedReviewsByProduct(Long productId, Pageable pageable);

  /** Lấy danh sách review của user hiện tại (phân trang) */
  Page<ReviewResponse> getMyReviews(Authentication authentication, Pageable pageable);

  // --- Admin Methods ---
  /** Lấy danh sách review theo trạng thái (cho Admin duyệt, phân trang) */
  Page<ReviewResponse> getReviewsByStatus(ReviewStatus status, Pageable pageable);

  /** Admin duyệt review */
  ReviewResponse approveReview(Long reviewId);

  /** Admin từ chối review */
  ReviewResponse rejectReview(Long reviewId);

  /** Admin xóa review */
  void deleteReview(Long reviewId);

  /** Lấy danh sách review cho các sản phẩm của Farmer hiện tại (phân trang) */
  Page<ReviewResponse> getReviewsForFarmerProducts(
      Authentication authentication, Pageable pageable);

  // Thêm phương thức lọc theo trạng thái
  Page<ReviewResponse> getReviewsForFarmerProductsByStatus(
      Authentication authentication, ReviewStatus status, Pageable pageable);
}
