package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
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
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    Product product =
        productRepository
            .findById(request.getProductId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Product", "id", request.getProductId()));

    //  Kiểm tra điều kiện viết review
    // 1. Kiểm tra xem user đã mua sản phẩm này chưa
    boolean hasPurchased =
        checkIfUserPurchasedProduct(consumer.getId(), request.getProductId(), request.getOrderId());
    if (!hasPurchased) {
      throw new BadRequestException("You must purchase this product to leave a review.");
    }

    // 2. Kiểm tra xem user đã review sản phẩm này trong đơn hàng này chưa (nếu có orderId)
    if (request.getOrderId() != null
        && reviewRepository.existsByConsumerIdAndProductIdAndOrderId(
            consumer.getId(), request.getProductId(), request.getOrderId())) {
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
      if (order != null
          && order.getBuyer().getId().equals(consumer.getId())) { // Đảm bảo đúng đơn hàng của user
        review.setOrder(order);
      } else {
        log.warn(
            "Order ID {} provided in review request for product {} by user {} is invalid or does not belong to the user.",
            request.getOrderId(),
            request.getProductId(),
            consumer.getId());
        // Có thể throw lỗi hoặc bỏ qua orderId
      }
    }
    review.setStatus(ReviewStatus.APPROVED); // Đặt trạng thái là APPROVED ngay lập tức

    Review savedReview = reviewRepository.save(review);
    log.info(
        "Review created with id {} for product {} by user {}",
        savedReview.getId(),
        product.getId(),
        consumer.getId());

    updateProductAverageRating(product.getId());



    // notificationService.sendReviewPendingNotification(savedReview); // Xóa hoặc comment dòng này
    notificationService.sendReviewApprovedNotification(
        savedReview);

    return reviewMapper.toReviewResponse(savedReview);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ReviewResponse> getApprovedReviewsByProduct(Long productId, Pageable pageable) {
    if (!productRepository.existsById(productId)) {
      throw new ResourceNotFoundException("Product", "id", productId);
    }
    Page<Review> reviewPage =
        reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable);
    return reviewMapper.toReviewResponsePage(reviewPage);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ReviewResponse> getMyReviews(Authentication authentication, Pageable pageable) {
    User consumer = getUserFromAuthentication(authentication);
    Page<Review> reviewPage = reviewRepository.findByConsumerId(consumer.getId(), pageable);
    return reviewMapper.toReviewResponsePage(reviewPage);
  }

  // ****** IMPLEMENT PHƯƠNG THỨC NÀY ******
  @Override
  @Transactional(readOnly = true)
  public Page<ReviewResponse> getReviewsForFarmerProducts(
      Authentication authentication, Pageable pageable) {
    User farmer = getUserFromAuthentication(authentication); // Lấy user farmer đang đăng nhập
    Page<Review> reviewPage =
        reviewRepository.findReviewsForFarmerProducts(farmer.getId(), pageable);

    return reviewMapper.toReviewResponsePage(reviewPage);
  }

  // (Tùy chọn) Implement phương thức lọc theo trạng thái
  @Override
  @Transactional(readOnly = true)
  public Page<ReviewResponse> getReviewsForFarmerProductsByStatus(
      Authentication authentication, ReviewStatus status, Pageable pageable) {
    User farmer = getUserFromAuthentication(authentication);
    Page<Review> reviewPage =
        reviewRepository.findReviewsForFarmerProductsByStatus(farmer.getId(), status, pageable);

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
      //  Gửi thông báo cho người viết review
      notificationService.sendReviewApprovedNotification(savedReview); // Gọi NotificationService
      return reviewMapper.toReviewResponse(savedReview);
    } else {
      log.warn(
          "Admin tried to approve review {} which is already in status {}",
          reviewId,
          review.getStatus());
      throw new BadRequestException("Review cannot be approved from its current status.");
    }
  }

  @Override
  @Transactional
  public ReviewResponse rejectReview(Long reviewId) {
    Review review = findReviewById(reviewId);
    if (review.getStatus() == ReviewStatus.PENDING
        || review.getStatus() == ReviewStatus.APPROVED) { // Có thể từ chối cả review đã duyệt
      ReviewStatus previousStatus = review.getStatus();
      review.setStatus(ReviewStatus.REJECTED);
      Review savedReview = reviewRepository.save(review);
      // Cập nhật lại rating trung bình nếu review trước đó đã được duyệt
      if (previousStatus == ReviewStatus.APPROVED) {
        updateProductAverageRating(review.getProduct().getId());
      }
      log.info("Review {} rejected by admin.", reviewId);
      //  Gửi thông báo cho người viết review *
      notificationService.sendReviewRejectedNotification(savedReview); // Gọi NotificationService
      return reviewMapper.toReviewResponse(savedReview);
    } else {
      log.warn(
          "Admin tried to reject review {} which is already in status {}",
          reviewId,
          review.getStatus());
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

  //  HELPER METHOD
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      // Ném lỗi nếu không xác thực, vì API tạo review yêu cầu đăng nhập
      throw new AccessDeniedException("User is not authenticated to create a review.");
    }
    String email = authentication.getName(); // Lấy email/username từ Principal
    return userRepository
        .findByEmail(email) // Tìm trong DB
        .orElseThrow(
            () ->
                new UsernameNotFoundException(
                    "Authenticated user not found with email: "
                        + email)); // Ném lỗi nếu không thấy user trong DB
  }



  private Review findReviewById(Long reviewId) {
    return reviewRepository
        .findById(reviewId)
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


  private void updateProductAverageRating(Long productId) {
    log.info("===> START updateProductAverageRating for productId: {}", productId);
    Optional<Product> productOpt = productRepository.findById(productId);

    if (productOpt.isEmpty()) {
      log.warn("===> Product with ID {} not found. Cannot update ratings.", productId);
      return;
    }
    Product product = productOpt.get();
    log.info("===> Found product: {}, current avgRating: {}, current ratingCount: {}",
            product.getName(), product.getAverageRating(), product.getRatingCount());

    Optional<Object[]> resultOpt = reviewRepository.getAverageRatingAndCountByProductIdAndStatus(
            productId, ReviewStatus.APPROVED
    );

    float newAverageRating = 0.0f;
    int newRatingCount = 0;

    if (resultOpt.isPresent()) {
      Object[] outerArray = resultOpt.get(); // Đây là mảng Object[] bên ngoài
      log.info("===> Query result outerArray for productId {}: {}", productId, Arrays.toString(outerArray));

      // Kiểm tra xem phần tử đầu tiên có phải là một mảng và có đủ 2 phần tử không
      if (outerArray.length > 0 && outerArray[0] instanceof Object[]) {
        Object[] innerArray = (Object[]) outerArray[0]; // Đây là mảng [AVG, COUNT]
        log.info("===> Query result innerArray for productId {}: {}", productId, Arrays.toString(innerArray));

        if (innerArray.length == 2) {
          Double avgRatingDouble = null;
          if (innerArray[0] instanceof Number) {
            avgRatingDouble = ((Number) innerArray[0]).doubleValue();
          } else if (innerArray[0] != null) {
            log.warn("===> Unexpected type for average rating from innerArray[0]: {}", innerArray[0].getClass().getName());
          }

          Long ratingCountLong = 0L;
          if (innerArray[1] instanceof Number) {
            ratingCountLong = ((Number) innerArray[1]).longValue();
          } else if (innerArray[1] != null) {
            log.warn("===> Unexpected type for rating count from innerArray[1]: {}", innerArray[1].getClass().getName());
          }

          log.info("===> Parsed from innerArray for product {}: avgRatingDouble={}, ratingCountLong={}",
                  productId, avgRatingDouble, ratingCountLong);

          if (avgRatingDouble != null && ratingCountLong > 0) {
            newAverageRating = BigDecimal.valueOf(avgRatingDouble)
                    .setScale(1, RoundingMode.HALF_UP).floatValue();
            newRatingCount = ratingCountLong.intValue();
          } else {
            log.info("===> No valid ratings or count is zero from innerArray for product {}. Setting ratings to 0.", productId);
          }
        } else {
          log.warn("===> Inner array from query for productId {} did not return 2 elements (length: {}). Setting ratings to 0.", productId, innerArray.length);
        }
      } else {
        // Trường hợp này có thể xảy ra nếu không có review nào và query trả về một mảng rỗng hoặc một cấu trúc khác
        // Hoặc nếu query chỉ trả về một giá trị đơn (ví dụ chỉ COUNT là 0 và AVG là NULL không được trả về như một phần tử)
        // Kiểm tra xem có phải là trường hợp COUNT = 0 không
        if (outerArray.length == 1 && outerArray[0] instanceof Number && ((Number)outerArray[0]).longValue() == 0) {
          log.info("===> Query returned a single count of 0 for product {}. Setting ratings to 0.", productId);
        } else {
          log.warn("===> Query result outerArray for productId {} was not in the expected format (Object[] containing [AVG, COUNT]). Length: {}. Content: {}. Setting ratings to 0.",
                  productId, outerArray.length, Arrays.toString(outerArray));
        }
      }
    } else {
      log.info("===> No result from rating/count query for productId {}. Setting ratings to 0.", productId);
    }

    log.info("===> Calculated for product {}: newAverageRating={}, newRatingCount={}",
            productId, newAverageRating, newRatingCount);

    if (product.getAverageRating() != newAverageRating || product.getRatingCount() != newRatingCount) {
      product.setAverageRating(newAverageRating);
      product.setRatingCount(newRatingCount);
      log.info("===> Product {} stats CHANGED. Attempting to save: avgRating={}, ratingCount={}",
              product.getId(), newAverageRating, newRatingCount);
      try {
        productRepository.save(product);
        log.info("===> Product {} stats successfully SAVED.", product.getId());
      } catch (Exception e) {
        log.error("===> ERROR saving product {} after updating rating stats: {}", product.getId(), e.getMessage(), e);
      }
    } else {
      log.info("===> No change in rating stats for product {}. Skipping save.", product.getId());
    }
    log.info("===> END updateProductAverageRating for productId: {}", productId);
  }


}
