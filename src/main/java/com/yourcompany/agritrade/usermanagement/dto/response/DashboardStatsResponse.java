package com.yourcompany.agritrade.usermanagement.dto.response;

import java.math.BigDecimal;
import java.util.Map;
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
  private Long lowStockSupplies; // Nguồn cung B2B sắp hết
  private Long pendingSupplyRequests; // Yêu cầu đặt hàng chờ xác nhận

  // Admin specific
  private Long totalUsers;
  private Long totalFarmers;
  private Long totalConsumers;
  private Long totalBusinessBuyers;
  private Long pendingFarmerApprovals;
  private Long pendingProductApprovals; // Nếu có quy trình duyệt sản phẩm
  private Long pendingReviews; // Tổng review chờ duyệt

  private BigDecimal totalRevenuePreviousMonth; // Doanh thu tháng trước
  private Map<String, Long> orderStatusDistribution; // Phân bổ trạng thái đơn hàng
}
