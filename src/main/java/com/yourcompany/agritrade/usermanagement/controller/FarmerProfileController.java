package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.FarmerProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerProfileResponse;
import com.yourcompany.agritrade.usermanagement.service.FarmerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles/farmer")
@RequiredArgsConstructor
public class FarmerProfileController {

  private final FarmerProfileService farmerProfileService;

  // Endpoint để Farmer tạo hoặc cập nhật profile của chính mình
  @PutMapping("/me")
  //    @PreAuthorize("hasRole('FARMER')") // Chỉ Farmer mới được gọi API này
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<FarmerProfileResponse>> createOrUpdateMyProfile(
      Authentication authentication, @Valid @RequestBody FarmerProfileRequest request) {
    FarmerProfileResponse response =
        farmerProfileService.createOrUpdateFarmerProfile(authentication, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Farmer profile updated successfully"));
  }

  // Endpoint để xem profile của một Farmer bất kỳ (có thể dùng cho public hoặc user đã login)
  // Đặt ở controller riêng hoặc public controller nếu không cần login
  @GetMapping("/{userId}")
  // @PreAuthorize("isAuthenticated()") // Ví dụ: Yêu cầu login để xem
  public ResponseEntity<ApiResponse<FarmerProfileResponse>> getFarmerProfile(
      @PathVariable Long userId) {
    FarmerProfileResponse response = farmerProfileService.getFarmerProfile(userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // --- Các API cho Admin duyệt profile ---
  // @PostMapping("/{userId}/approve")
  // @PreAuthorize("hasRole('ADMIN')")
  // public ResponseEntity<ApiResponse<Void>> approveProfile(...) {}
  //
  // @PostMapping("/{userId}/reject")
  // @PreAuthorize("hasRole('ADMIN')")
  // public ResponseEntity<ApiResponse<Void>> rejectProfile(...) {}

}
