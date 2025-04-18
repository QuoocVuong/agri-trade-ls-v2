package com.yourcompany.agritradels.usermanagement.service.impl;

import com.yourcompany.agritradels.catalog.domain.ProductImage;
import com.yourcompany.agritradels.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritradels.catalog.domain.ProductStatus; // Import ProductStatus
import com.yourcompany.agritradels.catalog.repository.ProductRepository;
import com.yourcompany.agritradels.common.model.NotificationType;
import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.common.model.VerificationStatus; // Import VerificationStatus
import com.yourcompany.agritradels.common.model.ReviewStatus; // Import ReviewStatus
import com.yourcompany.agritradels.interaction.repository.ReviewRepository;
import com.yourcompany.agritradels.ordering.domain.Order;
import com.yourcompany.agritradels.ordering.domain.OrderStatus;
import com.yourcompany.agritradels.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritradels.ordering.mapper.OrderMapper; // Import OrderMapper
import com.yourcompany.agritradels.ordering.repository.OrderRepository;
import com.yourcompany.agritradels.usermanagement.domain.User;
import com.yourcompany.agritradels.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.RecentActivityResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.TimeSeriesDataPoint;
import com.yourcompany.agritradels.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritradels.usermanagement.repository.UserRepository;
import com.yourcompany.agritradels.usermanagement.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest; // Import PageRequest
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Import Sort
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // Mặc định là read-only cho các hàm get
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final FarmerProfileRepository farmerProfileRepository;
    private final ReviewRepository reviewRepository;
    private final OrderMapper orderMapper; // Inject OrderMapper

    private static final int LOW_STOCK_THRESHOLD = 5; // Ngưỡng tồn kho thấp
    private static final List<OrderStatus> PENDING_ORDER_STATUSES = Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);
    private static final List<OrderStatus> REVENUE_ORDER_STATUSES = Arrays.asList(OrderStatus.SHIPPING, OrderStatus.DELIVERED); // Trạng thái tính doanh thu

    @Override
    public DashboardStatsResponse getFarmerDashboardStats(Authentication authentication) {
        User farmer = getUserFromAuthentication(authentication);
        Long farmerId = farmer.getId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = todayEnd; // Tính đến hiện tại của tháng

        long totalOrdersToday = orderRepository.countByFarmerIdAndCreatedAtBetween(farmerId, todayStart, todayEnd);
        long totalOrdersThisMonth = orderRepository.countByFarmerIdAndCreatedAtBetween(farmerId, monthStart, monthEnd);
        BigDecimal totalRevenueToday = orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(farmerId, REVENUE_ORDER_STATUSES, todayStart, todayEnd);
        BigDecimal totalRevenueThisMonth = orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(farmerId, REVENUE_ORDER_STATUSES, monthStart, monthEnd);
        long pendingOrders = orderRepository.countByFarmerIdAndStatusIn(farmerId, PENDING_ORDER_STATUSES);
        long lowStockProducts = productRepository.countByFarmerIdAndStockQuantityLessThan(farmerId, LOW_STOCK_THRESHOLD);
        long pendingReviews = reviewRepository.countByProductFarmerIdAndStatus(farmerId, ReviewStatus.PENDING);

        return DashboardStatsResponse.builder()
                .totalOrdersToday(totalOrdersToday)
                .totalOrdersThisMonth(totalOrdersThisMonth)
                .totalRevenueToday(totalRevenueToday)
                .totalRevenueThisMonth(totalRevenueThisMonth)
                .pendingOrders(pendingOrders)
                .lowStockProducts(lowStockProducts)
                .pendingReviewsOnMyProducts(pendingReviews)
                .build();
    }

    @Override
    public List<OrderSummaryResponse> getRecentFarmerOrders(Authentication authentication, int limit) {
        User farmer = getUserFromAuthentication(authentication);
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        // Dùng lại hàm repo đã có JOIN FETCH nếu có, hoặc tạo query mới chỉ lấy field cần cho summary
        Page<Order> orderPage = orderRepository.findByFarmerIdWithDetails(farmer.getId(), pageable);
        return orderMapper.toOrderSummaryResponseList(orderPage.getContent());
    }

    // File: usermanagement/service/impl/DashboardServiceImpl.java
    @Override
    public List<TopProductResponse> getTopSellingFarmerProducts(Authentication authentication, int limit) {
        User farmer = getUserFromAuthentication(authentication);
        Pageable pageable = PageRequest.of(0, limit);
        // Gọi phương thức repo mới
        List<TopProductResponse> topProducts = productRepository.findTopSellingProductsByFarmerWithoutThumbnail(farmer.getId(), pageable);

        // Lấy danh sách Product ID
        List<Long> productIds = topProducts.stream().map(TopProductResponse::getProductId).collect(Collectors.toList());

        if (!productIds.isEmpty()) {
            // Query để lấy thông tin ảnh của các product này (tránh N+1)
            // Cần tạo phương thức trong ProductRepository hoặc ProductImageRepository
            // Ví dụ: Map<Long, String> productThumbnails = getProductThumbnails(productIds);

            // Tạm thời query từng cái (không tối ưu)
            topProducts.forEach(tp -> {
                productRepository.findById(tp.getProductId()).ifPresent(product -> {
                    String thumbnailUrl = product.getImages().stream() // Giả sử images được fetch hoặc query riêng
                            .filter(ProductImage::isDefault)
                            .findFirst()
                            .or(() -> product.getImages().stream().min(Comparator.comparing(ProductImage::getId)))
                            .map(ProductImage::getImageUrl)
                            .orElse(null);
                    tp.setThumbnailUrl(thumbnailUrl);
                });
            });
        }
        return topProducts;
    }

    @Override
    public DashboardStatsResponse getAdminDashboardStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = todayEnd;

        long totalOrdersToday = orderRepository.countByCreatedAtBetween(todayStart, todayEnd);
        long totalOrdersThisMonth = orderRepository.countByCreatedAtBetween(monthStart, monthEnd);
        BigDecimal totalRevenueToday = orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(REVENUE_ORDER_STATUSES, todayStart, todayEnd);
        BigDecimal totalRevenueThisMonth = orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(REVENUE_ORDER_STATUSES, monthStart, monthEnd);

        long totalUsers = userRepository.count(); // Đếm tất cả user (bao gồm cả đã xóa mềm nếu không có @Where)
        // Cần phương thức count không tính soft delete nếu User có @Where
        // long totalActiveUsers = userRepository.countByIsDeletedFalse();
        long totalFarmers = userRepository.countByRoleName(RoleType.ROLE_FARMER);
        long totalConsumers = userRepository.countByRoleName(RoleType.ROLE_CONSUMER);
        long totalBusinessBuyers = userRepository.countByRoleName(RoleType.ROLE_BUSINESS_BUYER);

        long pendingFarmerApprovals = farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING);
        long pendingProductApprovals = productRepository.countByStatus(ProductStatus.PENDING_APPROVAL); // Nếu có duyệt SP
        long pendingReviews = reviewRepository.countByStatus(ReviewStatus.PENDING);

        return DashboardStatsResponse.builder()
                .totalOrdersToday(totalOrdersToday)
                .totalOrdersThisMonth(totalOrdersThisMonth)
                .totalRevenueToday(totalRevenueToday)
                .totalRevenueThisMonth(totalRevenueThisMonth)
                .totalUsers(totalUsers) // Cần làm rõ là active hay total
                .totalFarmers(totalFarmers)
                .totalConsumers(totalConsumers)
                .totalBusinessBuyers(totalBusinessBuyers)
                .pendingFarmerApprovals(pendingFarmerApprovals)
                .pendingProductApprovals(pendingProductApprovals)
                .pendingReviews(pendingReviews)
                .pendingFarmerApprovals(getPendingApprovalCounts().getOrDefault("farmers", 0L))
                .pendingProductApprovals(getPendingApprovalCounts().getOrDefault("products", 0L))
                .pendingReviews(getPendingApprovalCounts().getOrDefault("reviews", 0L))
                .build();
    }


    @Override
    public List<TimeSeriesDataPoint<BigDecimal>> getDailyRevenueForAdminChart(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<Object[]> results = orderRepository.findDailyRevenueBetween(REVENUE_ORDER_STATUSES, startDateTime, endDateTime);

        return results.stream()
                .map(result -> {
                    LocalDate date = null; // Khởi tạo là null
                    if (result != null && result.length > 0 && result[0] != null) { // Kiểm tra null an toàn hơn
                        if (result[0] instanceof java.sql.Date) { // *** Kiểm tra instanceof java.sql.Date ***
                            date = ((java.sql.Date) result[0]).toLocalDate(); // *** Ép kiểu sang java.sql.Date ***
                        } else if (result[0] instanceof String) {
                            try { // Thêm try-catch cho parse
                                date = LocalDate.parse((String) result[0]);
                            } catch (Exception e) {
                                log.warn("Could not parse date string: {}", result[0], e);
                            }
                        } else if (result[0] instanceof java.util.Date) { // Xử lý trường hợp là java.util.Date (ít gặp hơn từ DB function)
                            date = new java.sql.Date(((java.util.Date) result[0]).getTime()).toLocalDate();
                        } else {
                            log.warn("Unexpected date type returned from query: {}", result[0].getClass().getName());
                        }
                    }

                    if (date != null && result.length > 1 && result[1] instanceof BigDecimal) { // Kiểm tra null cho date và kiểu của value
                        BigDecimal revenue = (BigDecimal) result[1];
                        return new TimeSeriesDataPoint<>(date, revenue);
                    } else {
                        log.warn("Skipping data point due to null date or invalid revenue value: date={}, value={}", date, result != null && result.length > 1 ? result[1] : "N/A");
                        return null; // Trả về null nếu không hợp lệ
                    }
                })
                .filter(Objects::nonNull) // Lọc bỏ các điểm dữ liệu null
                .collect(Collectors.toList());
    }


    @Override
    public List<TimeSeriesDataPoint<Long>> getDailyOrderCountForAdminChart(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<Object[]> results = orderRepository.findDailyOrderCountBetween(startDateTime, endDateTime);

        return results.stream()
                .map(result -> {
                    LocalDate date = null;
                    if (result != null && result.length > 0 && result[0] != null) {
                        if (result[0] instanceof java.sql.Date) { // *** Kiểm tra instanceof java.sql.Date ***
                            date = ((java.sql.Date) result[0]).toLocalDate(); // *** Ép kiểu sang java.sql.Date ***
                        } else if (result[0] instanceof String) {
                            try {
                                date = LocalDate.parse((String) result[0]);
                            } catch (Exception e) {
                                log.warn("Could not parse date string: {}", result[0], e);
                            }
                        } else if (result[0] instanceof java.util.Date) {
                            date = new java.sql.Date(((java.util.Date) result[0]).getTime()).toLocalDate();
                        } else {
                            log.warn("Unexpected date type returned from query: {}", result[0].getClass().getName());
                        }
                    }

                    if (date != null && result.length > 1 && result[1] instanceof Long) { // Kiểm tra null và kiểu Long
                        Long count = (Long) result[1];
                        return new TimeSeriesDataPoint<>(date, count);
                    } else {
                        log.warn("Skipping data point due to null date or invalid count value: date={}, value={}", date, result != null && result.length > 1 ? result[1] : "N/A");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    @Override
    public List<RecentActivityResponse> getRecentActivitiesForAdmin(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<RecentActivityResponse> activities = new ArrayList<>();

        // Lấy đơn hàng mới nhất
        orderRepository.findTopNByOrderByCreatedAtDesc(pageable).forEach(order ->
                activities.add(RecentActivityResponse.builder()
                        .id(order.getId())
                        .type(NotificationType.ORDER_PLACED) // Dùng tạm NotificationType
                        .description(String.format("Đơn hàng mới #%s từ %s", order.getOrderCode(), order.getBuyer().getFullName()))
                        .timestamp(order.getCreatedAt())
                        .link("/admin/orders/" + order.getId()) // Ví dụ link admin
                        .build())
        );

        // Lấy user mới nhất
        userRepository.findTopNByOrderByCreatedAtDesc(pageable).forEach(user ->
                activities.add(RecentActivityResponse.builder()
                        .id(user.getId())
                        .type(NotificationType.WELCOME)
                        .description(String.format("Người dùng mới: %s (%s)", user.getFullName(), user.getEmail()))
                        .timestamp(user.getCreatedAt())
                        .link("/admin/users/" + user.getId() + "/profile") // Ví dụ link admin
                        .build())
        );

        // Lấy review chờ duyệt mới nhất
        reviewRepository.findTopNByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING, pageable).forEach(review ->
                activities.add(RecentActivityResponse.builder()
                        .id(review.getId())
                        .type(NotificationType.REVIEW_PENDING) // Cần thêm type này vào Enum
                        .description(String.format("Đánh giá mới chờ duyệt cho sản phẩm ID %d", review.getProduct().getId()))
                        .timestamp(review.getCreatedAt())
                        .link("/admin/reviews/pending") // Ví dụ link admin
                        .build())
        );

        // Sắp xếp lại tất cả hoạt động theo thời gian giảm dần và giới hạn số lượng
        activities.sort(Comparator.comparing(RecentActivityResponse::getTimestamp).reversed());
        return activities.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> getPendingApprovalCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("farmers", farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING));
        counts.put("products", productRepository.countByStatus(ProductStatus.PENDING_APPROVAL));
        counts.put("reviews", reviewRepository.countByStatus(ReviewStatus.PENDING));
        return counts;
    }




    // Helper method
    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
}