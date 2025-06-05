package com.yourcompany.agritrade.interaction.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reviews") // <<< Base path cho Admin
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

  private final ReviewService reviewService;

  // Lấy review theo trạng thái
  @GetMapping
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getReviewsByStatus(
      @RequestParam(required = true) ReviewStatus status,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<ReviewResponse> reviews = reviewService.getReviewsByStatus(status, pageable);
    return ResponseEntity.ok(ApiResponse.success(reviews));
  }

  // Admin duyệt review
  @PostMapping("/{reviewId}/approve")
  public ResponseEntity<ApiResponse<ReviewResponse>> approveReview(@PathVariable Long reviewId) {
    ReviewResponse review = reviewService.approveReview(reviewId);
    return ResponseEntity.ok(ApiResponse.success(review, "Review approved"));
  }

  // Admin từ chối review
  @PostMapping("/{reviewId}/reject")
  public ResponseEntity<ApiResponse<ReviewResponse>> rejectReview(@PathVariable Long reviewId) {
    ReviewResponse review = reviewService.rejectReview(reviewId);
    return ResponseEntity.ok(ApiResponse.success(review, "Review rejected"));
  }

  // Admin xóa review
  @DeleteMapping("/{reviewId}")
  public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
    reviewService.deleteReview(reviewId);
    return ResponseEntity.ok(ApiResponse.success("Review deleted"));
  }
}
