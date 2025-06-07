
package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.dto.request.SupplyOrderPlacementRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.SupplyOrderRequestResponse;
import com.yourcompany.agritrade.ordering.service.SupplyOrderRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/supply-requests") // Base path mới
@RequiredArgsConstructor
public class SupplyOrderRequestController {

    private final SupplyOrderRequestService requestService;

    @PostMapping
    @PreAuthorize("isAuthenticated()") // Buyer (Consumer/Business) có thể tạo
    public ResponseEntity<ApiResponse<SupplyOrderRequestResponse>> createRequest(
            Authentication authentication,
            @Valid @RequestBody SupplyOrderPlacementRequest request) {
        SupplyOrderRequestResponse createdRequest = requestService.createSupplyOrderRequest(authentication, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(createdRequest, "Supply order request submitted successfully."));
    }

    @GetMapping("/my-sent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<SupplyOrderRequestResponse>>> getMySentRequests(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt,desc") Pageable pageable) {
        Page<SupplyOrderRequestResponse> requests = requestService.getMySentRequests(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping("/my-received")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ApiResponse<Page<SupplyOrderRequestResponse>>> getMyReceivedRequests(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt,desc") Pageable pageable) {
        Page<SupplyOrderRequestResponse> requests = requestService.getMyReceivedRequests(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SupplyOrderRequestResponse>> getRequestDetails(
            Authentication authentication, @PathVariable Long requestId) {
        SupplyOrderRequestResponse requestDetails = requestService.getRequestDetails(authentication, requestId);
        return ResponseEntity.ok(ApiResponse.success(requestDetails));
    }

    @PostMapping("/{requestId}/accept")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> acceptRequest(
            Authentication authentication, @PathVariable Long requestId) {
        // Service sẽ tạo Order từ Request này
        OrderResponse createdOrder = requestService.acceptSupplyOrderRequest(authentication, requestId);
        return ResponseEntity.ok(ApiResponse.success(createdOrder, "Supply request accepted and order created."));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('FARMER')")
    public ResponseEntity<ApiResponse<SupplyOrderRequestResponse>> rejectRequest(
            Authentication authentication, @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> payload) {
        String reason = (payload != null) ? payload.get("reason") : null;
        SupplyOrderRequestResponse rejectedRequest = requestService.rejectSupplyOrderRequest(authentication, requestId, reason);
        return ResponseEntity.ok(ApiResponse.success(rejectedRequest, "Supply request rejected."));
    }

    @PostMapping("/{requestId}/cancel-by-buyer") // Hoặc dùng DELETE nếu thấy phù hợp hơn
    @PreAuthorize("isAuthenticated()") // Buyer phải đăng nhập
    public ResponseEntity<ApiResponse<Void>> cancelRequestByBuyer(
            Authentication authentication, @PathVariable Long requestId) {
        requestService.cancelSupplyOrderRequestByBuyer(authentication, requestId);
        return ResponseEntity.ok(ApiResponse.success("Supply order request cancelled successfully by buyer."));
    }
}