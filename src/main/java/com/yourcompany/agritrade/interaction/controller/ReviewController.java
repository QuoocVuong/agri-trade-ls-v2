package com.yourcompany.agritrade.interaction.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  // Tạo review mới (yêu cầu đăng nhập)
  @PostMapping
  @PreAuthorize(
      "hasAnyRole('CONSUMER', 'BUSINESS_BUYER')") // Hoặc "hasAnyRole('CONSUMER', 'BUSINESS_BUYER')"
  public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
      Authentication authentication, @Valid @RequestBody ReviewRequest request) {
    ReviewResponse createdReview = reviewService.createReview(authentication, request);
    // Trả về 201 Created
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(createdReview, "Review submitted successfully."));
  }

  // Lấy danh sách review đã duyệt của một sản phẩm (public)
  @GetMapping("/product/{productId}")
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getApprovedReviewsByProduct(
      @PathVariable Long productId,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<ReviewResponse> reviews = reviewService.getApprovedReviewsByProduct(productId, pageable);
    return ResponseEntity.ok(ApiResponse.success(reviews));
  }


  // Lấy danh sách review của user hiện tại (yêu cầu đăng nhập)
  @GetMapping("/my")
  @PreAuthorize("isAuthenticated()") // Yêu cầu đã đăng nhập
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getMyReviews(
      Authentication authentication,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    log.info(
        "API /api/reviews/my called by user: {}", authentication.getName()); // Thêm log để kiểm tra
    Page<ReviewResponse> reviews = reviewService.getMyReviews(authentication, pageable);
    return ResponseEntity.ok(ApiResponse.success(reviews));
  }


}
