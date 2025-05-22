package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.service.AdminUserService;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
// @PreAuthorize("isAuthenticated()") // Yêu cầu xác thực cho tất cả API trong controller này
public class UserController {

  private final UserService userService;

  private final AdminUserService adminUserService;

  // Endpoint lấy thông tin profile của user đang đăng nhập
  @GetMapping("/me/profile")
  @PreAuthorize("isAuthenticated()") // Yêu cầu login
  public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUserProfile(
      Authentication authentication) {
    UserProfileResponse profile = userService.getCurrentUserProfile(authentication);
    return ResponseEntity.ok(ApiResponse.success(profile));
  }

  // Endpoint đổi mật khẩu
  @PutMapping("/me/password")
  @PreAuthorize("isAuthenticated()") // Yêu cầu login
  public ResponseEntity<ApiResponse<Void>> changePassword(
      Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) {
    userService.changePassword(authentication, request);
    return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
  }

  // Endpoint user tự cập nhật thông tin cơ bản
  @PutMapping("/me")
  @PreAuthorize("isAuthenticated()") // Yêu cầu login
  public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
      Authentication authentication, @Valid @RequestBody UserUpdateRequest request) {
    UserResponse updatedUser = userService.updateCurrentUserProfile(authentication, request);
    return ResponseEntity.ok(ApiResponse.success(updatedUser, "Profile updated successfully"));
  }

  /// --- Admin Endpoints ---

  // Lấy danh sách tất cả user (phân trang) - Chỉ Admin
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  //@PreAuthorize("hasAuthority('USER_READ_ALL')") // Cách mới dùng permission
  public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsersForAdmin(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String role, // Nhận role là String
      @RequestParam(required = false) Boolean isActive, // Nhận isActive
      @PageableDefault(size = 20, sort = "createdAt,desc")
          Pageable pageable) { // Thêm phân trang mặc định

    RoleType roleEnum = null;
    if (StringUtils.hasText(role)) {
      try {
        roleEnum = RoleType.valueOf(role.toUpperCase());
      } catch (IllegalArgumentException e) {
        // log.warn("Invalid role value received: {}", role);
      }
    }
    Page<UserResponse> users = adminUserService.getAllUsers(pageable, roleEnum, keyword, isActive);
    return ResponseEntity.ok(ApiResponse.success(users));
  }

  // Lấy profile đầy đủ của user bất kỳ theo ID - Chỉ Admin
  @GetMapping("/{id}/profile")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfileById(
      @PathVariable Long id) {
    UserProfileResponse profile = userService.getUserProfileById(id);
    return ResponseEntity.ok(ApiResponse.success(profile));
  }

  // Cập nhật trạng thái active/inactive của user - Chỉ Admin
  @PutMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  //@PreAuthorize("hasAuthority('USER_UPDATE_STATUS')")
  public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
      @PathVariable Long id, @RequestParam boolean isActive) {
    UserResponse updatedUser = userService.updateUserStatus(id, isActive);
    return ResponseEntity.ok(ApiResponse.success(updatedUser, "User status updated"));
  }

  // Cập nhật vai trò của user - Chỉ Admin
  @PutMapping("/{id}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<UserResponse>> updateUserRoles(
      @PathVariable Long id, @RequestBody Set<RoleType> roles) {
    UserResponse updatedUser = userService.updateUserRoles(id, roles);
    return ResponseEntity.ok(ApiResponse.success(updatedUser, "User roles updated"));
  }

  // --- Public Endpoint (Ví dụ) ---
  // Lấy thông tin cơ bản (không nhạy cảm) của user theo ID để hiển thị public
  // Cần tạo DTO riêng (ví dụ PublicUserResponse) và phương thức service riêng
  // @GetMapping("/{id}/public")
  // public ResponseEntity<ApiResponse<PublicUserResponse>> getPublicUserInfo(@PathVariable Long id)
  // { ... }

}
