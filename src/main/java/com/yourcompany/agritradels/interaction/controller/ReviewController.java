package com.yourcompany.agritradels.interaction.controller;

import com.yourcompany.agritradels.common.dto.ApiResponse;
import com.yourcompany.agritradels.common.model.ReviewStatus;
import com.yourcompany.agritradels.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritradels.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritradels.interaction.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // Tạo review mới (yêu cầu đăng nhập)
    @PostMapping
    @PreAuthorize("isAuthenticated()") // Hoặc "hasAnyRole('CONSUMER', 'BUSINESS_BUYER')"
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            Authentication authentication,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse createdReview = reviewService.createReview(authentication, request);
        // Trả về 201 Created
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(createdReview, "Review submitted successfully and pending approval."));
    }

    // Lấy danh sách review đã duyệt của một sản phẩm (public)
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getApprovedReviewsByProduct(
            @PathVariable Long productId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReviewResponse> reviews = reviewService.getApprovedReviewsByProduct(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    // Lấy danh sách review của user hiện tại (yêu cầu đăng nhập)
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getMyReviews(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReviewResponse> reviews = reviewService.getMyReviews(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    // --- Admin Endpoints (có thể tách ra AdminReviewController) ---

    // Lấy review cần duyệt (PENDING) - Chỉ Admin
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getPendingReviews(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByStatus(ReviewStatus.PENDING, pageable);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    // Admin duyệt review
    @PostMapping("/admin/{reviewId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReviewResponse>> approveReview(@PathVariable Long reviewId) {
        ReviewResponse review = reviewService.approveReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(review, "Review approved"));
    }

    // Admin từ chối review
    @PostMapping("/admin/{reviewId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReviewResponse>> rejectReview(@PathVariable Long reviewId) {
        ReviewResponse review = reviewService.rejectReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success(review, "Review rejected"));
    }

    // Admin xóa review
    @DeleteMapping("/admin/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted"));
    }
}