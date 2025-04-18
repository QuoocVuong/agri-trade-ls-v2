package com.yourcompany.agritradels.ordering.repository;

import com.yourcompany.agritradels.ordering.domain.Order;
import com.yourcompany.agritradels.ordering.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // Import
import org.springframework.data.jpa.repository.Query; // Import
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> { // Thêm JpaSpecificationExecutor

    Optional<Order> findByOrderCode(String orderCode);

    // Lấy đơn hàng của người mua (phân trang)
    // Cần JOIN FETCH để tránh N+1 khi lấy buyer/farmer info trong list
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.buyer JOIN FETCH o.farmer WHERE o.buyer.id = :buyerId",
            countQuery = "SELECT count(o) FROM Order o WHERE o.buyer.id = :buyerId")
    Page<Order> findByBuyerIdWithDetails(Long buyerId, Pageable pageable);

    // Lấy đơn hàng của nông dân (phân trang)
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.buyer JOIN FETCH o.farmer WHERE o.farmer.id = :farmerId",
            countQuery = "SELECT count(o) FROM Order o WHERE o.farmer.id = :farmerId")
    Page<Order> findByFarmerIdWithDetails(Long farmerId, Pageable pageable);

    // Lấy chi tiết đơn hàng theo ID (bao gồm items, payments) - Cần JOIN FETCH
    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH o.payments p " +
            "LEFT JOIN FETCH o.buyer b " +
            "LEFT JOIN FETCH o.farmer f " +
            "WHERE o.id = :orderId")
    Optional<Order> findByIdWithDetails(Long orderId);

    // Lấy chi tiết đơn hàng theo Code (bao gồm items, payments)
    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH o.payments p " +
            "LEFT JOIN FETCH o.buyer b " +
            "LEFT JOIN FETCH o.farmer f " +
            "WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithDetails(String orderCode);

    // Tìm đơn hàng theo ID và Buyer ID (kiểm tra ownership)
    Optional<Order> findByIdAndBuyerId(Long orderId, Long buyerId);

    // Tìm đơn hàng theo ID và Farmer ID (kiểm tra ownership)
    Optional<Order> findByIdAndFarmerId(Long orderId, Long farmerId);

    // Kiểm tra xem user đã mua sản phẩm này trong một đơn hàng đã hoàn thành chưa
    @Query("SELECT CASE WHEN COUNT(o.id) > 0 THEN true ELSE false END " +
            "FROM Order o JOIN o.orderItems oi " +
            "WHERE o.buyer.id = :buyerId " +
            "AND oi.product.id = :productId " +
            "AND o.status = com.yourcompany.agritradels.ordering.domain.OrderStatus.DELIVERED") // Chỉ tính đơn đã giao
    boolean existsByBuyerIdAndStatusAndOrderItemsProductId(
            @Param("buyerId") Long buyerId,
            @Param("productId") Long productId);

    // Kiểm tra xem user đã mua sản phẩm trong đơn hàng cụ thể chưa (và đơn hàng đó là của user)
    @Query("SELECT CASE WHEN COUNT(o.id) > 0 THEN true ELSE false END " +
            "FROM Order o JOIN o.orderItems oi " +
            "WHERE o.id = :orderId " +
            "AND o.buyer.id = :buyerId " +
            "AND oi.product.id = :productId")
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
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.farmer.id = :farmerId AND o.status IN :statuses AND o.createdAt BETWEEN :start AND :end")
    BigDecimal sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(@Param("farmerId") Long farmerId, @Param("statuses") List<OrderStatus> statuses, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.buyer.id = :buyerId AND o.status IN :statuses AND o.createdAt BETWEEN :start AND :end")
    BigDecimal sumTotalAmountByBuyerIdAndStatusInAndCreatedAtBetween(@Param("buyerId") Long buyerId, @Param("statuses") List<OrderStatus> statuses, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN :statuses AND o.createdAt BETWEEN :start AND :end")
    BigDecimal sumTotalAmountByStatusInAndCreatedAtBetween(@Param("statuses") List<OrderStatus> statuses, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end); // Cho Admin

    // Đếm đơn hàng đang chờ xử lý của Farmer
    Long countByFarmerIdAndStatusIn(Long farmerId, List<OrderStatus> pendingStatuses);

    // Đếm đơn hàng theo trạng thái (cho Admin)
    Long countByStatus(OrderStatus status);



    // Lấy tổng doanh thu theo ngày trong khoảng thời gian
    @Query("SELECT FUNCTION('DATE', o.createdAt), COALESCE(SUM(o.totalAmount), 0) " +
            "FROM Order o " +
            "WHERE o.status IN :statuses AND o.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', o.createdAt) " +
            "ORDER BY FUNCTION('DATE', o.createdAt) ASC")
    List<Object[]> findDailyRevenueBetween(@Param("statuses") List<OrderStatus> statuses,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    // Lấy số lượng đơn hàng theo ngày trong khoảng thời gian
    @Query("SELECT FUNCTION('DATE', o.createdAt), COUNT(o.id) " +
            "FROM Order o " +
            "WHERE o.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', o.createdAt) " +
            "ORDER BY FUNCTION('DATE', o.createdAt) ASC")
    List<Object[]> findDailyOrderCountBetween(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    // Lấy các đơn hàng mới nhất (cho recent activities)
    List<Order> findTopNByOrderByCreatedAtDesc(Pageable pageable); // N là số lượng giới hạn

}