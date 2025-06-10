package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
    extends JpaRepository<Order, Long>,
        JpaSpecificationExecutor<Order> { // Thêm JpaSpecificationExecutor

//  Optional<Order> findByOrderCode(String orderCode);

  // Lấy đơn hàng của người mua (phân trang)
  @Query(
      value =
          "SELECT o FROM Order o "
              + "JOIN FETCH o.buyer b "
              + "JOIN FETCH o.farmer f "
              + "LEFT JOIN FETCH f.farmerProfile fp "
              + // Thêm fetch farmerProfile
              "WHERE b.id = :buyerId",
      countQuery = "SELECT count(o) FROM Order o WHERE o.buyer.id = :buyerId")
  Page<Order> findByBuyerIdWithDetails(Long buyerId, Pageable pageable);

  // Lấy đơn hàng của nông dân (phân trang)
  @Query(
      value =
          "SELECT o FROM Order o "
              + "JOIN FETCH o.buyer b "
              + "JOIN FETCH o.farmer f "
              + "LEFT JOIN FETCH f.farmerProfile fp "
              + // Thêm fetch farmerProfile
              "WHERE f.id = :farmerId",
      countQuery = "SELECT count(o) FROM Order o WHERE o.farmer.id = :farmerId")
  Page<Order> findByFarmerIdWithDetails(Long farmerId, Pageable pageable);

  // để gợi ý cho Hibernate cách fetch dữ liệu.
// Cách này thường ổn định và dễ bảo trì hơn.
  @EntityGraph(attributePaths = {
          "buyer",
          "farmer",
          "farmer.farmerProfile",
          "orderItems",
          "orderItems.product",
          "payments"
  })
  Optional<Order> findById(Long orderId); // Ghi đè phương thức findById mặc định


//  @Query("SELECT o FROM Order o WHERE o.id = :orderId") // Thêm lại @Query đơn giản
//  @EntityGraph(attributePaths = {
//          "buyer",
//          "farmer",
//          "farmer.farmerProfile",
//          "orderItems",
//          "orderItems.product",
//          "payments"
//  })
//  Optional<Order> findByIdWithDetails(@Param("orderId") Long orderId);

  // Làm tương tự cho findByOrderCodeWithDetails nếu nó cũng gây lỗi
  @EntityGraph(attributePaths = {
          "buyer",
          "farmer",
          "farmer.farmerProfile",
          "orderItems",
          "orderItems.product",
          "payments"
  })
  Optional<Order> findByOrderCode(String orderCode); // Ghi đè findByOrderCode


//  // Lấy chi tiết đơn hàng theo Code (bao gồm items, payments)
//  @Query("SELECT o FROM Order o WHERE o.orderCode = :orderCode") // Thêm lại @Query đơn giản
//  @EntityGraph(attributePaths = {
//          "buyer",
//          "farmer",
//          "farmer.farmerProfile",
//          "orderItems",
//          "orderItems.product",
//          "payments"
//  })
//  Optional<Order> findByOrderCodeWithDetails(@Param("orderCode") String orderCode);

  // Tìm đơn hàng theo ID và Buyer ID (kiểm tra ownership)
  Optional<Order> findByIdAndBuyerId(Long orderId, Long buyerId);

  // Tìm đơn hàng theo ID và Farmer ID (kiểm tra ownership)
  Optional<Order> findByIdAndFarmerId(Long orderId, Long farmerId);

  // Kiểm tra xem user đã mua sản phẩm này trong một đơn hàng đã hoàn thành chưa
  @Query(
      "SELECT CASE WHEN COUNT(o.id) > 0 THEN true ELSE false END "
          + "FROM Order o JOIN o.orderItems oi "
          + "WHERE o.buyer.id = :buyerId "
          + "AND oi.product.id = :productId "
          + "AND o.status = com.yourcompany.agritrade.ordering.domain.OrderStatus.DELIVERED") // Chỉ
  // tính đơn đã giao
  boolean existsByBuyerIdAndStatusAndOrderItemsProductId(
      @Param("buyerId") Long buyerId, @Param("productId") Long productId);

  // Kiểm tra xem user đã mua sản phẩm trong đơn hàng cụ thể chưa (và đơn hàng đó là của user)
  @Query(
      "SELECT CASE WHEN COUNT(o.id) > 0 THEN true ELSE false END "
          + "FROM Order o JOIN o.orderItems oi "
          + "WHERE o.id = :orderId "
          + "AND o.buyer.id = :buyerId "
          + "AND oi.product.id = :productId")
  boolean existsByIdAndBuyerIdAndOrderItemsProductId(
      @Param("orderId") Long orderId,
      @Param("buyerId") Long buyerId,
      @Param("productId") Long productId);

  // --- Truy vấn cho Dashboard ---

  // Đếm đơn hàng theo thời gian và farmer/buyer
  Long countByFarmerIdAndCreatedAtBetween(Long farmerId, LocalDateTime start, LocalDateTime end);

  Long countByBuyerIdAndCreatedAtBetween(Long buyerId, LocalDateTime start, LocalDateTime end);

  Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end); // Cho Admin

  // Tính tổng doanh thu theo thời gian và farmer/buyer (chỉ tính đơn đã hoàn thành/đang giao?)
  @Query(
      "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.farmer.id = :farmerId AND o.status IN :statuses AND o.createdAt BETWEEN :start AND :end")
  BigDecimal sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
      @Param("farmerId") Long farmerId,
      @Param("statuses") List<OrderStatus> statuses,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.buyer.id = :buyerId AND o.status IN :statuses AND o.createdAt BETWEEN :start AND :end")
  BigDecimal sumTotalAmountByBuyerIdAndStatusInAndCreatedAtBetween(
      @Param("buyerId") Long buyerId,
      @Param("statuses") List<OrderStatus> statuses,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN :statuses AND o.createdAt BETWEEN :start AND :end")
  BigDecimal sumTotalAmountByStatusInAndCreatedAtBetween(
      @Param("statuses") List<OrderStatus> statuses,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end); // Cho Admin

  // Đếm đơn hàng đang chờ xử lý của Farmer
  Long countByFarmerIdAndStatusIn(Long farmerId, List<OrderStatus> pendingStatuses);

  // Đếm đơn hàng theo trạng thái (cho Admin)
  Long countByStatus(OrderStatus status);

  // Lấy tổng doanh thu theo ngày trong khoảng thời gian
  @Query(
      "SELECT FUNCTION('DATE', o.createdAt), COALESCE(SUM(o.totalAmount), 0) "
          + "FROM Order o "
          + "WHERE o.status IN :statuses AND o.createdAt BETWEEN :start AND :end "
          + "GROUP BY FUNCTION('DATE', o.createdAt) "
          + "ORDER BY FUNCTION('DATE', o.createdAt) ASC")
  List<Object[]> findDailyRevenueBetween(
      @Param("statuses") List<OrderStatus> statuses,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  // Lấy số lượng đơn hàng theo ngày trong khoảng thời gian
  @Query(
      "SELECT FUNCTION('DATE', o.createdAt), COUNT(o.id) "
          + "FROM Order o "
          + "WHERE o.createdAt BETWEEN :start AND :end "
          + "GROUP BY FUNCTION('DATE', o.createdAt) "
          + "ORDER BY FUNCTION('DATE', o.createdAt) ASC")
  List<Object[]> findDailyOrderCountBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  // ===== QUERIES MỚI CHO FARMER CHART =====
  /**
   * Query đếm số đơn hàng theo ngày cho farmer cụ thể. Trả về Tuple với cột 0 là LocalDate
   * (orderDate), cột 1 là Long (orderCount).
   */
  @Query(
      """
           SELECT FUNCTION('DATE', o.createdAt) as orderDate, COUNT(o.id) as orderCount
           FROM Order o
           WHERE o.farmer.id = :farmerId
           AND o.createdAt BETWEEN :startDateTime AND :endDateTime
           GROUP BY orderDate
           ORDER BY orderDate ASC
           """)
  List<Tuple> countOrdersByDayForFarmer(
      @Param("farmerId") Long farmerId,
      @Param("startDateTime") LocalDateTime startDateTime,
      @Param("endDateTime") LocalDateTime endDateTime);

  /**
   * Query tính tổng doanh thu theo ngày cho farmer cụ thể (theo trạng thái cụ thể). Trả về Tuple
   * với cột 0 là LocalDate (orderDate), cột 1 là BigDecimal (dailyRevenue).
   */
  @Query(
      """
           SELECT FUNCTION('DATE', o.createdAt) as orderDate, SUM(o.totalAmount) as dailyRevenue
           FROM Order o
           WHERE o.farmer.id = :farmerId
           AND o.createdAt BETWEEN :startDateTime AND :endDateTime
           AND o.status IN :statuses
           GROUP BY orderDate
           ORDER BY orderDate ASC
           """)
  List<Tuple> sumRevenueByDayForFarmer(
      @Param("farmerId") Long farmerId,
      @Param("startDateTime") LocalDateTime startDateTime,
      @Param("endDateTime") LocalDateTime endDateTime,
      @Param("statuses") List<OrderStatus> statuses);

  // ======================================

  // Lấy các đơn hàng mới nhất (cho recent activities)
  List<Order> findTopNByOrderByCreatedAtDesc(Pageable pageable); // N là số lượng giới hạn

  // Trong OrderRepository.java
  @Query("SELECT o.buyer.id, SUM(o.totalAmount) as totalSpent " +
          "FROM Order o " +
          "WHERE o.status IN :statuses AND o.createdAt BETWEEN :start AND :end " +
          "GROUP BY o.buyer.id " +
          "ORDER BY totalSpent DESC")
  List<Object[]> findTopBuyersByTotalSpent(
          @Param("statuses") List<OrderStatus> statuses,
          @Param("start") LocalDateTime start,
          @Param("end") LocalDateTime end,
          Pageable pageable);




  @Query("SELECT o.farmer.id, SUM(o.totalAmount) as totalRevenue " +
          "FROM Order o " +
          "WHERE o.status IN :statuses AND o.createdAt BETWEEN :start AND :end " +
          "GROUP BY o.farmer.id " +
          "ORDER BY totalRevenue DESC")
  List<Object[]> findTopFarmersByRevenue(
          @Param("statuses") List<OrderStatus> statuses,
          @Param("start") LocalDateTime start,
          @Param("end") LocalDateTime end,
          Pageable pageable);
}
