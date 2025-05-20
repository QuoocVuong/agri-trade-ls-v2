package com.yourcompany.agritrade.usermanagement.dto.response; // Đặt trong usermanagement hoặc

// common

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder // Giúp tạo đối tượng dễ dàng hơn
public class DashboardStatsResponse {
  // Farmer & Admin
  private Long totalOrdersToday;
  private Long totalOrdersThisMonth;
  private BigDecimal totalRevenueToday;
  private BigDecimal totalRevenueThisMonth;

  // Farmer specific
  private Long pendingOrders; // Đơn hàng mới/đang xử lý của farmer
  private Long lowStockProducts; // Sản phẩm sắp hết hàng của farmer
  private Long pendingReviewsOnMyProducts; // Review mới cho sp của farmer

  // Admin specific
  private Long totalUsers;
  private Long totalFarmers;
  private Long totalConsumers;
  private Long totalBusinessBuyers;
  private Long pendingFarmerApprovals;
  private Long pendingProductApprovals; // Nếu có quy trình duyệt sản phẩm
  private Long pendingReviews; // Tổng review chờ duyệt
}
