package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.usermanagement.dto.request.FarmerRejectRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/farmers") // *** Base path cho API quản lý farmer của Admin ***
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Yêu cầu quyền Admin cho tất cả API trong controller này
public class AdminFarmerController {

  private final AdminUserService adminUserService;

  // API lấy danh sách Farmer đang chờ duyệt (PENDING)
  @GetMapping("/pending")
  public ResponseEntity<ApiResponse<Page<UserProfileResponse>>> getPendingFarmers(
      @PageableDefault(size = 10, sort = "createdAt,asc") Pageable pageable) {
    Page<UserProfileResponse> pendingFarmers =
        adminUserService.getPendingFarmers(pageable);
    return ResponseEntity.ok(ApiResponse.success(pendingFarmers));
  }

  // API lấy danh sách tất cả Farmer (có thể thêm filter)
  @GetMapping
  public ResponseEntity<ApiResponse<Page<UserProfileResponse>>> getAllFarmers(
      @RequestParam(required = false)
          VerificationStatus verificationStatus,
      @RequestParam(required = false) String keyword,
      @PageableDefault(size = 15, sort = "createdAt,desc") Pageable pageable) {
    // Cần thêm phương thức getAllFarmers vào AdminUserService
    Page<UserProfileResponse> allFarmers =
        adminUserService.getAllFarmers(verificationStatus, keyword, pageable);
    return ResponseEntity.ok(ApiResponse.success(allFarmers));
  }

  // API duyệt Farmer
  @PostMapping("/{userId}/approve")
  public ResponseEntity<ApiResponse<Void>> approveFarmer(
      @PathVariable Long userId, Authentication authentication) {
    adminUserService.approveFarmer(userId, authentication);
    return ResponseEntity.ok(ApiResponse.success("Farmer approved successfully."));
  }

  // API từ chối Farmer
  @PostMapping("/{userId}/reject")
  public ResponseEntity<ApiResponse<Void>> rejectFarmer(
      @PathVariable Long userId,
      @RequestBody(required = false) FarmerRejectRequest request,
      Authentication authentication) {
    String reason = (request != null) ? request.getReason() : null;
    adminUserService.rejectFarmer(
        userId, reason, authentication);
    return ResponseEntity.ok(ApiResponse.success("Farmer rejected successfully."));
  }


}
