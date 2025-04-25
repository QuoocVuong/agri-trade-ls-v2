package com.yourcompany.agritrade.usermanagement.service;

// Cập nhật import cho DTOs
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;

public interface UserService {
    UserResponse registerUser(UserRegistrationRequest registrationRequest); // Đổi tên tham số
    // Lấy thông tin profile đầy đủ của user đang đăng nhập
    UserProfileResponse getCurrentUserProfile(Authentication authentication);

    // Cập nhật mật khẩu

    // Cập nhật thông tin cơ bản của user đang đăng nhập
    UserResponse updateCurrentUserProfile(Authentication authentication, UserUpdateRequest updateRequest);

    void changePassword(Authentication authentication, PasswordChangeRequest passwordChangeRequest);


    /**
     * Xác thực email của người dùng bằng token.
     * @param token Mã xác thực từ email.
     * @return true nếu xác thực thành công.
    // * @throws BadRequestException nếu token không hợp lệ hoặc hết hạn.
     */
    boolean verifyEmail(String token);

    /**
     * Bắt đầu quá trình quên mật khẩu cho email được cung cấp.
     * Gửi email chứa link reset nếu email tồn tại.
     * @param email Email của người dùng quên mật khẩu.
     */
    void initiatePasswordReset(String email);

    /**
     * Hoàn tất việc reset mật khẩu bằng token và mật khẩu mới.
     * @param token Mã reset từ email.
     * @param newPassword Mật khẩu mới.
     //* @throws BadRequestException nếu token không hợp lệ hoặc hết hạn.
     */
    void resetPassword(String token, String newPassword);

    // --- Các phương thức cho Admin ---
    Page<UserResponse> getAllUsers(Pageable pageable);

    UserProfileResponse getUserProfileById(Long id); // Lấy profile đầy đủ theo ID

    UserResponse updateUserStatus(Long id, boolean isActive);

    UserResponse updateUserRoles(Long id, Set<RoleType> roles);

    // Có thể thêm phương thức xóa mềm user
     //void softDeleteUser(Long id);

    // ===== PHƯƠNG THỨC MỚI =====
    /**
     * Lấy danh sách nông dân nổi bật (ví dụ: theo số người theo dõi).
     * @param limit Số lượng tối đa cần lấy.
     * @return Danh sách FarmerSummaryResponse.
     */
    List<FarmerSummaryResponse> getFeaturedFarmers(int limit);
    // ===========================
}