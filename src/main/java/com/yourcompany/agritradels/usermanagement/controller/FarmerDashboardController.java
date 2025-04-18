package com.yourcompany.agritradels.usermanagement.controller; // Đặt trong usermanagement hoặc module dashboard riêng

import com.yourcompany.agritradels.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritradels.common.dto.ApiResponse;
import com.yourcompany.agritradels.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritradels.usermanagement.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/farmer/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARMER')")
public class FarmerDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(Authentication authentication) {
        DashboardStatsResponse stats = dashboardService.getFarmerDashboardStats(authentication);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> getRecentOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "5") int limit) { // Lấy 5 đơn gần nhất mặc định
        List<OrderSummaryResponse> orders = dashboardService.getRecentFarmerOrders(authentication, Math.max(1, limit)); // Đảm bảo limit > 0
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "5") int limit) { // Lấy 5 sản phẩm bán chạy nhất
        List<TopProductResponse> products = dashboardService.getTopSellingFarmerProducts(authentication, Math.max(1, limit));
        return ResponseEntity.ok(ApiResponse.success(products));
    }
}