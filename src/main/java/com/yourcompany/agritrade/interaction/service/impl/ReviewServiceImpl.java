package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product; // Import Product
import com.yourcompany.agritrade.catalog.repository.ProductRepository; // Import ProductRepo
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.mapper.ReviewMapper;
import com.yourcompany.agritrade.interaction.repository.ReviewRepository;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.Order; // Import Order
import com.yourcompany.agritrade.ordering.repository.OrderRepository; // Import OrderRepo
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal; // Import BigDecimal
import java.math.RoundingMode; // Import RoundingMode
import java.util.Optional; // Import Optional

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository; // Inject OrderRepository
    private final ReviewMapper reviewMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReviewResponse createReview(Authentication authentication, ReviewRequest request) {
        User consumer = getUserFromAuthentication(authentication);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        // --- Kiểm tra điều kiện viết review (quan trọng) ---
        // 1. Kiểm tra xem user đã mua sản phẩm này chưa (khuyến nghị)
        boolean hasPurchased = checkIfUserPurchasedProduct(consumer.getId(), request.getProductId(), request.getOrderId());
        if (!hasPurchased) {
            throw new BadRequestException("You must purchase this product to leave a review.");
        }

        // 2. Kiểm tra xem user đã review sản phẩm này trong đơn hàng này chưa (nếu có orderId)
        if (request.getOrderId() != null && reviewRepository.existsByConsumerIdAndProductIdAndOrderId(consumer.getId(), request.getProductId(), request.getOrderId())) {
            throw new BadRequestException("You have already reviewed this product for this order.");
        }
        // 3. Hoặc kiểm tra xem user đã review sản phẩm này bao giờ chưa (nếu không cần theo đơn hàng)
         if (reviewRepository.existsByConsumerIdAndProductId(consumer.getId(), request.getProductId())) {
             throw new BadRequestException("You have already reviewed this product.");
         }


        Review review = reviewMapper.requestToReview(request);
        review.setConsumer(consumer);
        review.setProduct(product);
        // Liên kết với Order nếu có ID
        if (request.getOrderId() != null) {
            Order order = orderRepository.findById(request.getOrderId()).orElse(null);
            if (order != null && order.getBuyer().getId().equals(consumer.getId())) { // Đảm bảo đúng đơn hàng của user
                review.setOrder(order);
            } else {
                log.warn("Order ID {} provided in review request for product {} by user {} is invalid or does not belong to the user.",
                        request.getOrderId(), request.getProductId(), consumer.getId());
                // Có thể throw lỗi hoặc bỏ qua orderId
            }
        }
        review.setStatus(ReviewStatus.PENDING); // Chờ duyệt

        Review savedReview = reviewRepository.save(review);
        log.info("Review created with id {} for product {} by user {}", savedReview.getId(), product.getId(), consumer.getId());
         //notificationService.sendReviewPendingNotification(savedReview);
        return reviewMapper.toReviewResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getApprovedReviewsByProduct(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        Page<Review> reviewPage = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable);
        return reviewMapper.toReviewResponsePage(reviewPage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(Authentication authentication, Pageable pageable) {
        User consumer = getUserFromAuthentication(authentication);
        Page<Review> reviewPage = reviewRepository.findByConsumerId(consumer.getId(), pageable);
        return reviewMapper.toReviewResponsePage(reviewPage);
    }

    // --- Admin Methods ---

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByStatus(ReviewStatus status, Pageable pageable) {
        Page<Review> reviewPage = reviewRepository.findByStatus(status, pageable);
        return reviewMapper.toReviewResponsePage(reviewPage);
    }

    @Override
    @Transactional
    public ReviewResponse approveReview(Long reviewId) {
        Review review = findReviewById(reviewId);
        if (review.getStatus() == ReviewStatus.PENDING || review.getStatus() == ReviewStatus.REJECTED) {
            review.setStatus(ReviewStatus.APPROVED);
            Review savedReview = reviewRepository.save(review);
            // Cập nhật lại rating trung bình của sản phẩm sau khi duyệt
            updateProductAverageRating(review.getProduct().getId());
            log.info("Review {} approved by admin.", reviewId);
            // *** Gửi thông báo cho người viết review ***
            notificationService.sendReviewApprovedNotification(savedReview); // Gọi NotificationService
            return reviewMapper.toReviewResponse(savedReview);
        } else {
            log.warn("Admin tried to approve review {} which is already in status {}", reviewId, review.getStatus());
            throw new BadRequestException("Review cannot be approved from its current status.");
        }
    }

    @Override
    @Transactional
    public ReviewResponse rejectReview(Long reviewId) {
        Review review = findReviewById(reviewId);
        if (review.getStatus() == ReviewStatus.PENDING || review.getStatus() == ReviewStatus.APPROVED) { // Có thể từ chối cả review đã duyệt
            ReviewStatus previousStatus = review.getStatus();
            review.setStatus(ReviewStatus.REJECTED);
            Review savedReview = reviewRepository.save(review);
            // Cập nhật lại rating trung bình nếu review trước đó đã được duyệt
            if (previousStatus == ReviewStatus.APPROVED) {
                updateProductAverageRating(review.getProduct().getId());
            }
            log.info("Review {} rejected by admin.", reviewId);
            // *** Gửi thông báo cho người viết review ***
            notificationService.sendReviewRejectedNotification(savedReview); // Gọi NotificationService
            return reviewMapper.toReviewResponse(savedReview);
        } else {
            log.warn("Admin tried to reject review {} which is already in status {}", reviewId, review.getStatus());
            throw new BadRequestException("Review cannot be rejected from its current status.");
        }
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = findReviewById(reviewId);
        ReviewStatus statusBeforeDelete = review.getStatus();
        Long productId = review.getProduct().getId();

        reviewRepository.delete(review);
        log.info("Review {} deleted by admin.", reviewId);

        // Cập nhật lại rating nếu review bị xóa là review đã duyệt
        if (statusBeforeDelete == ReviewStatus.APPROVED) {
            updateProductAverageRating(productId);
        }
    }

    // *** SỬA LẠI HELPER METHOD NÀY ***
    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            // Ném lỗi nếu không xác thực, vì API tạo review yêu cầu đăng nhập
            throw new AccessDeniedException("User is not authenticated to create a review.");
        }
        String email = authentication.getName(); // Lấy email/username từ Principal
        return userRepository.findByEmail(email) // Tìm trong DB
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user not found with email: " + email)); // Ném lỗi nếu không thấy user trong DB
    }
    // ********************************

    private Review findReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
    }
    private boolean checkIfUserPurchasedProduct(Long userId, Long productId, Long orderId) {
        // Ưu tiên kiểm tra theo orderId nếu được cung cấp
        if (orderId != null) {
            return orderRepository.existsByIdAndBuyerIdAndOrderItemsProductId(orderId, userId, productId);
        } else {
            // Nếu không có orderId, kiểm tra xem user đã từng mua và nhận hàng thành công chưa
            return orderRepository.existsByBuyerIdAndStatusAndOrderItemsProductId(userId, productId);
        }
    }

    // Cập nhật rating trung bình và số lượng rating cho sản phẩm
    private void updateProductAverageRating(Long productId) {
        productRepository.findById(productId).ifPresent(product -> {
            // Dùng query đã tạo trong ReviewRepository
            Optional<Object[]> result = reviewRepository.getAverageRatingAndCountByProductIdAndStatus(productId, ReviewStatus.APPROVED);
            if (result.isPresent() && result.get().length == 2 && result.get()[0] != null) {
                Double avgRatingDouble = (Double) result.get()[0];
                Long ratingCountLong = (Long) result.get()[1];

                // Làm tròn rating đến 1 chữ số thập phân
                BigDecimal avgRating = BigDecimal.valueOf(avgRatingDouble).setScale(1, RoundingMode.HALF_UP);

                product.setAverageRating(avgRating.floatValue());
                product.setRatingCount(ratingCountLong.intValue());
            } else {
                // Nếu không có review nào được duyệt
                product.setAverageRating(0.0f);
                product.setRatingCount(0);
            }
            productRepository.save(product);
            log.debug("Updated average rating for product {}: avg={}, count={}", productId, product.getAverageRating(), product.getRatingCount());
        });
    }
}