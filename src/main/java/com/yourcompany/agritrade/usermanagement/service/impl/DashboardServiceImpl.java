package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.interaction.repository.ReviewRepository;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerChartDataResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.RecentActivityResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.TimeSeriesDataPoint;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import com.yourcompany.agritrade.usermanagement.service.DashboardService;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private static final List<OrderStatus> PENDING_ORDER_STATUSES =
      Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);
  private static final List<OrderStatus> REVENUE_ORDER_STATUSES =
      Arrays.asList(OrderStatus.SHIPPING, OrderStatus.DELIVERED); // Trạng thái tính doanh thu

  @Override
  public DashboardStatsResponse getFarmerDashboardStats(Authentication authentication) {
    User farmer = getUserFromAuthentication(authentication);
    Long farmerId = farmer.getId();
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
    LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    LocalDateTime monthEnd = todayEnd; // Tính đến hiện tại của tháng

    long totalOrdersToday =
        orderRepository.countByFarmerIdAndCreatedAtBetween(farmerId, todayStart, todayEnd);
    long totalOrdersThisMonth =
        orderRepository.countByFarmerIdAndCreatedAtBetween(farmerId, monthStart, monthEnd);
    BigDecimal totalRevenueToday =
        orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
            farmerId, REVENUE_ORDER_STATUSES, todayStart, todayEnd);
    BigDecimal totalRevenueThisMonth =
        orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
            farmerId, REVENUE_ORDER_STATUSES, monthStart, monthEnd);
    long pendingOrders =
        orderRepository.countByFarmerIdAndStatusIn(farmerId, PENDING_ORDER_STATUSES);
    long lowStockProducts =
        productRepository.countByFarmerIdAndStockQuantityLessThan(farmerId, LOW_STOCK_THRESHOLD);
    long pendingReviews =
        reviewRepository.countByProductFarmerIdAndStatus(farmerId, ReviewStatus.PENDING);

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
  public List<OrderSummaryResponse> getRecentFarmerOrders(
      Authentication authentication, int limit) {
    User farmer = getUserFromAuthentication(authentication);
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    // Dùng lại hàm repo đã có JOIN FETCH nếu có, hoặc tạo query mới chỉ lấy field cần cho summary
    Page<Order> orderPage = orderRepository.findByFarmerIdWithDetails(farmer.getId(), pageable);
    return orderMapper.toOrderSummaryResponseList(orderPage.getContent());
  }

  // File: usermanagement/service/impl/DashboardServiceImpl.java
  @Override
  public List<TopProductResponse> getTopSellingFarmerProducts(
      Authentication authentication, int limit) {
    User farmer = getUserFromAuthentication(authentication);
    Pageable pageable = PageRequest.of(0, limit);
    // Gọi phương thức repo mới
    List<TopProductResponse> topProducts =
        productRepository.findTopSellingProductsByFarmerWithoutThumbnail(farmer.getId(), pageable);

    // Lấy danh sách Product ID
    List<Long> productIds =
        topProducts.stream().map(TopProductResponse::getProductId).collect(Collectors.toList());

    if (!productIds.isEmpty()) {
      // Query để lấy thông tin ảnh của các product này (tránh N+1)
      // Cần tạo phương thức trong ProductRepository hoặc ProductImageRepository
      // Ví dụ: Map<Long, String> productThumbnails = getProductThumbnails(productIds);

      // Tạm thời query từng cái (không tối ưu)
      topProducts.forEach(
          tp -> {
            productRepository
                .findById(tp.getProductId())
                .ifPresent(
                    product -> {
                      String thumbnailUrl =
                          product.getImages().stream() // Giả sử images được fetch hoặc query riêng
                              .filter(ProductImage::isDefault)
                              .findFirst()
                              .or(
                                  () ->
                                      product.getImages().stream()
                                          .min(Comparator.comparing(ProductImage::getId)))
                              .map(ProductImage::getImageUrl)
                              .orElse(null);
                      tp.setThumbnailUrl(thumbnailUrl);
                    });
          });
    }
    return topProducts;
  }

  // ===== IMPLEMENT PHƯƠNG THỨC MỚI CHO FARMER ORDER COUNT CHART =====
  @Override
  public List<FarmerChartDataResponse> getFarmerOrderCountChartData(
      Authentication authentication, LocalDate startDate, LocalDate endDate) {
    User farmer = getUserFromAuthentication(authentication);
    Long farmerId = farmer.getId();

    LocalDateTime startDateTime = startDate.atStartOfDay();
    LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

    // Gọi query mới trong repository
    List<Tuple> dailyCounts =
        orderRepository.countOrdersByDayForFarmer(farmerId, startDateTime, endDateTime);

    // Tạo Map từ kết quả query (LocalDate -> Long count)
    Map<LocalDate, Long> countsMap =
        dailyCounts.stream()
            .collect(
                Collectors.toMap(
                    tuple -> {
                      // *** SỬA CÁCH LẤY DATE TỪ TUPLE ***
                      // Lấy kiểu Date gốc từ DB (thường là java.sql.Date hoặc java.util.Date)
                      Object dateObject = tuple.get(0); // Lấy cột đầu tiên (ngày)
                      if (dateObject instanceof java.sql.Date) {
                        return ((java.sql.Date) dateObject).toLocalDate();
                      } else if (dateObject instanceof Date) { // Fallback cho java.util.Date
                        return ((Date) dateObject)
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                      } else {
                        // Xử lý trường hợp không mong muốn hoặc log lỗi
                        log.error(
                            "Unexpected date type in tuple for order count chart: {}",
                            dateObject != null ? dateObject.getClass() : "null");
                        // Có thể trả về một giá trị mặc định hoặc ném lỗi tùy logic
                        return LocalDate.MIN; // Ví dụ trả về ngày nhỏ nhất để dễ lọc bỏ
                      }
                      // **********************************
                    },
                    tuple -> tuple.get(1, Long.class) // Lấy số lượng (cột 1)
                    ));

    // Tạo danh sách đầy đủ các ngày trong khoảng thời gian
    List<LocalDate> dateRange =
        Stream.iterate(startDate, date -> date.plusDays(1))
            .limit(startDate.until(endDate).getDays() + 1)
            .toList();

    // Tạo kết quả cuối cùng, điền 0 cho những ngày không có đơn hàng
    return dateRange.stream()
        .map(
            date -> {
              Long count = countsMap.getOrDefault(date, 0L); // Lấy count hoặc 0 nếu không có
              // Sử dụng constructor ChartDataDto(LocalDate, Long) nếu có, hoặc tạo mới
              return new FarmerChartDataResponse(date, count); // Dùng constructor (LocalDate, Long)
              // Hoặc nếu ChartDataDto có constructor (LocalDate, Long):
              // return new ChartDataDto(date, count);
            })
        .collect(Collectors.toList());
  }

  // =================================================================

  // ===== IMPLEMENT PHƯƠNG THỨC MỚI CHO FARMER REVENUE CHART =====
  @Override
  public List<FarmerChartDataResponse> getFarmerRevenueChartData(
      Authentication authentication, LocalDate startDate, LocalDate endDate) {
    User farmer = getUserFromAuthentication(authentication);
    Long farmerId = farmer.getId();

    LocalDateTime startDateTime = startDate.atStartOfDay();
    LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

    // Gọi query mới trong repository, sử dụng REVENUE_ORDER_STATUSES
    List<Tuple> dailyRevenues =
        orderRepository.sumRevenueByDayForFarmer(
            farmerId,
            startDateTime,
            endDateTime,
            REVENUE_ORDER_STATUSES // Chỉ tính doanh thu từ các trạng thái này
            );

    // Tạo Map từ kết quả query (LocalDate -> BigDecimal revenue)
    Map<LocalDate, BigDecimal> revenuesMap =
        dailyRevenues.stream()
            .filter(tuple -> tuple.get(1, BigDecimal.class) != null)
            .collect(
                Collectors.toMap(
                    tuple -> {
                      // *** SỬA CÁCH LẤY DATE TỪ TUPLE (TƯƠNG TỰ NHƯ TRÊN) ***
                      Object dateObject = tuple.get(0);
                      if (dateObject instanceof java.sql.Date) {
                        return ((java.sql.Date) dateObject).toLocalDate();
                      } else if (dateObject instanceof Date) {
                        return ((Date) dateObject)
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                      } else {
                        log.error(
                            "Unexpected date type in tuple for revenue chart: {}",
                            dateObject != null ? dateObject.getClass() : "null");
                        return LocalDate.MIN;
                      }
                      // *******************************************************
                    },
                    tuple -> tuple.get(1, BigDecimal.class) // Lấy doanh thu (cột 1)
                    ));

    // Tạo danh sách đầy đủ các ngày trong khoảng thời gian
    List<LocalDate> dateRange =
        Stream.iterate(startDate, date -> date.plusDays(1))
            .limit(startDate.until(endDate).getDays() + 1)
            .toList();

    // Tạo kết quả cuối cùng, điền 0 cho những ngày không có doanh thu
    return dateRange.stream()
        .map(
            date -> {
              BigDecimal revenue =
                  revenuesMap.getOrDefault(date, BigDecimal.ZERO); // Lấy revenue hoặc 0
              // Sử dụng constructor ChartDataDto(String, BigDecimal)
              return new FarmerChartDataResponse(
                  date.toString(), revenue); // Dùng constructor (String, BigDecimal)
            })
        .collect(Collectors.toList());
  }

  // ==============================================================

  @Override
  public DashboardStatsResponse getAdminDashboardStats() {
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
    LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    LocalDateTime monthEnd = todayEnd;

    long totalOrdersToday = orderRepository.countByCreatedAtBetween(todayStart, todayEnd);
    long totalOrdersThisMonth = orderRepository.countByCreatedAtBetween(monthStart, monthEnd);
    BigDecimal totalRevenueToday =
        orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
            REVENUE_ORDER_STATUSES, todayStart, todayEnd);
    BigDecimal totalRevenueThisMonth =
        orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
            REVENUE_ORDER_STATUSES, monthStart, monthEnd);

    long totalUsers =
        userRepository.count(); // Đếm tất cả user (bao gồm cả đã xóa mềm nếu không có @Where)
    // Cần phương thức count không tính soft delete nếu User có @Where
    // long totalActiveUsers = userRepository.countByIsDeletedFalse();
    long totalFarmers = userRepository.countByRoleName(RoleType.ROLE_FARMER);
    long totalConsumers = userRepository.countByRoleName(RoleType.ROLE_CONSUMER);
    long totalBusinessBuyers = userRepository.countByRoleName(RoleType.ROLE_BUSINESS_BUYER);

    long pendingFarmerApprovals =
        farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING);
    long pendingProductApprovals =
        productRepository.countByStatus(ProductStatus.PENDING_APPROVAL); // Nếu có duyệt SP
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

  //    @Override
  //    public List<TimeSeriesDataPoint<BigDecimal>> getDailyRevenueForAdminChart(LocalDate
  // startDate, LocalDate endDate) {
  //        LocalDateTime startDateTime = startDate.atStartOfDay();
  //        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
  //        List<Object[]> results = orderRepository.findDailyRevenueBetween(REVENUE_ORDER_STATUSES,
  // startDateTime, endDateTime);
  //
  //        return results.stream()
  //                .map(result -> {
  //                    LocalDate date = null; // Khởi tạo là null
  //                    if (result != null && result.length > 0 && result[0] != null) { // Kiểm tra
  // null an toàn hơn
  //                        if (result[0] instanceof java.sql.Date) { // *** Kiểm tra instanceof
  // java.sql.Date ***
  //                            date = ((java.sql.Date) result[0]).toLocalDate(); // *** Ép kiểu
  // sang java.sql.Date ***
  //                        } else if (result[0] instanceof String) {
  //                            try { // Thêm try-catch cho parse
  //                                date = LocalDate.parse((String) result[0]);
  //                            } catch (Exception e) {
  //                                log.warn("Could not parse date string: {}", result[0], e);
  //                            }
  //                        } else if (result[0] instanceof java.util.Date) { // Xử lý trường hợp là
  // java.util.Date (ít gặp hơn từ DB function)
  //                            date = new java.sql.Date(((java.util.Date)
  // result[0]).getTime()).toLocalDate();
  //                        } else {
  //                            log.warn("Unexpected date type returned from query: {}",
  // result[0].getClass().getName());
  //                        }
  //                    }
  //
  //                    if (date != null && result.length > 1 && result[1] instanceof BigDecimal) {
  // // Kiểm tra null cho date và kiểu của value
  //                        BigDecimal revenue = (BigDecimal) result[1];
  //                        return new TimeSeriesDataPoint<>(date, revenue);
  //                    } else {
  //                        log.warn("Skipping data point due to null date or invalid revenue value:
  // date={}, value={}", date, result != null && result.length > 1 ? result[1] : "N/A");
  //                        return null; // Trả về null nếu không hợp lệ
  //                    }
  //                })
  //                .filter(Objects::nonNull) // Lọc bỏ các điểm dữ liệu null
  //                .collect(Collectors.toList());
  //    }

  @Override
  public List<TimeSeriesDataPoint<BigDecimal>> getDailyRevenueForAdminChart(
      LocalDate startDate, LocalDate endDate) {
    // ... implementation đã có ...
    // Đảm bảo logic xử lý kiểu Date từ DB là chính xác (như code bạn đã cung cấp)
    LocalDateTime startDateTime = startDate.atStartOfDay();
    LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
    List<Object[]> results =
        orderRepository.findDailyRevenueBetween(REVENUE_ORDER_STATUSES, startDateTime, endDateTime);

    // Tạo Map từ kết quả query (LocalDate -> BigDecimal revenue)
    Map<LocalDate, BigDecimal> revenuesMap =
        results.stream()
            .filter(
                result ->
                    result != null
                        && result.length > 1
                        && result[0] != null
                        && result[1] != null) // Kiểm tra null kỹ hơn
            .collect(
                Collectors.toMap(
                    result -> parseDateFromResult(result[0]), // Dùng helper function
                    result -> (BigDecimal) result[1]));

    // Tạo danh sách đầy đủ các ngày trong khoảng thời gian
    List<LocalDate> dateRange =
        Stream.iterate(startDate, date -> date.plusDays(1))
            .limit(startDate.until(endDate).getDays() + 1)
            .toList();

    // Tạo kết quả cuối cùng, điền 0 cho những ngày không có doanh thu
    return dateRange.stream()
        .map(
            date ->
                new TimeSeriesDataPoint<>(date, revenuesMap.getOrDefault(date, BigDecimal.ZERO)))
        .collect(Collectors.toList());
  }

  //    @Override
  //    public List<TimeSeriesDataPoint<Long>> getDailyOrderCountForAdminChart(LocalDate startDate,
  // LocalDate endDate) {
  //        LocalDateTime startDateTime = startDate.atStartOfDay();
  //        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
  //        List<Object[]> results = orderRepository.findDailyOrderCountBetween(startDateTime,
  // endDateTime);
  //
  //        return results.stream()
  //                .map(result -> {
  //                    LocalDate date = null;
  //                    if (result != null && result.length > 0 && result[0] != null) {
  //                        if (result[0] instanceof java.sql.Date) { // *** Kiểm tra instanceof
  // java.sql.Date ***
  //                            date = ((java.sql.Date) result[0]).toLocalDate(); // *** Ép kiểu
  // sang java.sql.Date ***
  //                        } else if (result[0] instanceof String) {
  //                            try {
  //                                date = LocalDate.parse((String) result[0]);
  //                            } catch (Exception e) {
  //                                log.warn("Could not parse date string: {}", result[0], e);
  //                            }
  //                        } else if (result[0] instanceof java.util.Date) {
  //                            date = new java.sql.Date(((java.util.Date)
  // result[0]).getTime()).toLocalDate();
  //                        } else {
  //                            log.warn("Unexpected date type returned from query: {}",
  // result[0].getClass().getName());
  //                        }
  //                    }
  //
  //                    if (date != null && result.length > 1 && result[1] instanceof Long) { //
  // Kiểm tra null và kiểu Long
  //                        Long count = (Long) result[1];
  //                        return new TimeSeriesDataPoint<>(date, count);
  //                    } else {
  //                        log.warn("Skipping data point due to null date or invalid count value:
  // date={}, value={}", date, result != null && result.length > 1 ? result[1] : "N/A");
  //                        return null;
  //                    }
  //                })
  //                .filter(Objects::nonNull)
  //                .collect(Collectors.toList());
  //    }

  @Override
  public List<TimeSeriesDataPoint<Long>> getDailyOrderCountForAdminChart(
      LocalDate startDate, LocalDate endDate) {
    // ... implementation đã có ...
    // Đảm bảo logic xử lý kiểu Date từ DB là chính xác
    LocalDateTime startDateTime = startDate.atStartOfDay();
    LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
    List<Object[]> results = orderRepository.findDailyOrderCountBetween(startDateTime, endDateTime);

    // Tạo Map từ kết quả query (LocalDate -> Long count)
    Map<LocalDate, Long> countsMap =
        results.stream()
            .filter(
                result ->
                    result != null && result.length > 1 && result[0] != null && result[1] != null)
            .collect(
                Collectors.toMap(
                    result -> parseDateFromResult(result[0]), result -> (Long) result[1]));

    // Tạo danh sách đầy đủ các ngày trong khoảng thời gian
    List<LocalDate> dateRange =
        Stream.iterate(startDate, date -> date.plusDays(1))
            .limit(startDate.until(endDate).getDays() + 1)
            .toList();

    // Tạo kết quả cuối cùng, điền 0 cho những ngày không có đơn hàng
    return dateRange.stream()
        .map(date -> new TimeSeriesDataPoint<>(date, countsMap.getOrDefault(date, 0L)))
        .collect(Collectors.toList());
  }

  @Override
  public List<RecentActivityResponse> getRecentActivitiesForAdmin(int limit) {
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    List<RecentActivityResponse> activities = new ArrayList<>();

    // Lấy đơn hàng mới nhất
    orderRepository
        .findTopNByOrderByCreatedAtDesc(pageable)
        .forEach(
            order ->
                activities.add(
                    RecentActivityResponse.builder()
                        .id(order.getId())
                        .type(NotificationType.ORDER_PLACED) // Dùng tạm NotificationType
                        .description(
                            String.format(
                                "Đơn hàng mới #%s từ %s",
                                order.getOrderCode(), order.getBuyer().getFullName()))
                        .timestamp(order.getCreatedAt())
                        .link("/admin/orders/" + order.getId()) // Ví dụ link admin
                        .build()));

    // Lấy user mới nhất
    userRepository
        .findTopNByOrderByCreatedAtDesc(pageable)
        .forEach(
            user ->
                activities.add(
                    RecentActivityResponse.builder()
                        .id(user.getId())
                        .type(NotificationType.WELCOME)
                        .description(
                            String.format(
                                "Người dùng mới: %s (%s)", user.getFullName(), user.getEmail()))
                        .timestamp(user.getCreatedAt())
                        .link("/admin/users/" + user.getId() + "/profile") // Ví dụ link admin
                        .build()));

    // Lấy review chờ duyệt mới nhất
    reviewRepository
        .findTopNByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING, pageable)
        .forEach(
            review ->
                activities.add(
                    RecentActivityResponse.builder()
                        .id(review.getId())
                        .type(NotificationType.REVIEW_PENDING) // Cần thêm type này vào Enum
                        .description(
                            String.format(
                                "Đánh giá mới chờ duyệt cho sản phẩm ID %d",
                                review.getProduct().getId()))
                        .timestamp(review.getCreatedAt())
                        .link("/admin/reviews/pending") // Ví dụ link admin
                        .build()));

    // Sắp xếp lại tất cả hoạt động theo thời gian giảm dần và giới hạn số lượng
    activities.sort(Comparator.comparing(RecentActivityResponse::getTimestamp).reversed());
    return activities.stream().limit(limit).collect(Collectors.toList());
  }

  @Override
  public Map<String, Long> getPendingApprovalCounts() {
    Map<String, Long> counts = new HashMap<>();
    counts.put(
        "farmers", farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING));
    counts.put("products", productRepository.countByStatus(ProductStatus.PENDING_APPROVAL));
    counts.put("reviews", reviewRepository.countByStatus(ReviewStatus.PENDING));
    return counts;
  }

  // Helper method
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  // Helper function để parse Date từ Object trả về của query (tránh lặp code)
  private LocalDate parseDateFromResult(Object dateObject) {
    if (dateObject instanceof java.sql.Date) {
      return ((java.sql.Date) dateObject).toLocalDate();
    } else if (dateObject instanceof LocalDate) { // Một số DB driver có thể trả về LocalDate
      return (LocalDate) dateObject;
    } else if (dateObject instanceof String) {
      try {
        return LocalDate.parse((String) dateObject);
      } catch (Exception e) {
        log.warn("Could not parse date string: {}", dateObject, e);
        return null; // Hoặc throw exception nếu muốn chặt chẽ hơn
      }
    } else if (dateObject instanceof java.util.Date) { // Xử lý java.util.Date
      return new java.sql.Date(((java.util.Date) dateObject).getTime()).toLocalDate();
    }
    log.warn(
        "Unexpected date type returned from query: {}",
        dateObject != null ? dateObject.getClass().getName() : "null");
    return null; // Hoặc throw exception
  }
}
