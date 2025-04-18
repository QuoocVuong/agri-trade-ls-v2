package com.yourcompany.agritradels.usermanagement.controller;

import com.yourcompany.agritradels.common.dto.ApiResponse;
import com.yourcompany.agritradels.usermanagement.dto.request.BusinessProfileRequest;
import com.yourcompany.agritradels.usermanagement.dto.response.BusinessProfileResponse;
import com.yourcompany.agritradels.usermanagement.service.BusinessProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles/business") // Đổi đường dẫn base
@RequiredArgsConstructor
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    // Endpoint để Business Buyer tạo hoặc cập nhật profile của chính mình
    @PutMapping("/me")
    //@PreAuthorize("hasRole('BUSINESS_BUYER')") // Chỉ Business Buyer mới được gọi
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> createOrUpdateMyProfile(
            Authentication authentication,
            @Valid @RequestBody BusinessProfileRequest request) {
        BusinessProfileResponse response = businessProfileService.createOrUpdateBusinessProfile(authentication, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Business profile updated successfully"));
    }

    // Endpoint để xem profile của một Business Buyer bất kỳ (có thể dùng cho public hoặc user đã login)
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()") // Ví dụ: Yêu cầu login để xem
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> getBusinessProfile(@PathVariable Long userId) {
        BusinessProfileResponse response = businessProfileService.getBusinessProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Có thể thêm các API cho Admin nếu cần (ví dụ: xem danh sách business profiles...)
}