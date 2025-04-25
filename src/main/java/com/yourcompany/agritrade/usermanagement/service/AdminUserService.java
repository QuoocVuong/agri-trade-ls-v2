package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus; // Import VerificationStatus
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication; // Import nếu cần thông tin admin thực hiện

import java.util.Set;

public interface AdminUserService {

    /**
     * Lấy danh sách tất cả người dùng (có phân trang và lọc).
     * @param pageable Thông tin phân trang và sắp xếp.
     * @param roleName Lọc theo vai trò (tùy chọn).
     * @param keyword Lọc theo từ khóa (tên, email, sđt - tùy chọn).
     * @param isActive Lọc theo trạng thái active (tùy chọn).
     * @return Trang danh sách người dùng (UserResponse).
     */
    Page<UserResponse> getAllUsers(Pageable pageable, RoleType roleName, String keyword, Boolean isActive);

    /**
     * Lấy thông tin profile đầy đủ của một người dùng cụ thể theo ID.
     * @param userId ID của người dùng.
     * @return Thông tin profile đầy đủ.
     //* @throws ResourceNotFoundException Nếu không tìm thấy user.
     */
    UserProfileResponse getUserProfileById(Long userId);

    /**
     * Cập nhật trạng thái active/inactive của một người dùng.
     * @param userId ID của người dùng.
     * @param isActive Trạng thái mới (true=active, false=inactive).
     * @param adminAuth Thông tin admin thực hiện (để log, tùy chọn).
     * @return Thông tin user đã cập nhật.
     //* @throws ResourceNotFoundException Nếu không tìm thấy user.
     */
    UserResponse updateUserStatus(Long userId, boolean isActive, Authentication adminAuth);

    /**
     * Cập nhật vai trò cho một người dùng.
     * @param userId ID của người dùng.
     * @param roles Tập hợp các vai trò mới (RoleType).
     * @param adminAuth Thông tin admin thực hiện (để log, tùy chọn).
     * @return Thông tin user đã cập nhật.
     //* @throws ResourceNotFoundException Nếu không tìm thấy user hoặc role.
     */
    UserResponse updateUserRoles(Long userId, Set<RoleType> roles, Authentication adminAuth);

    /**
     * Lấy danh sách các Farmer đang chờ duyệt (PENDING).
     * @param pageable Thông tin phân trang và sắp xếp.
     * @return Trang danh sách Farmer Profile đầy đủ (UserProfileResponse).
     */
    Page<UserProfileResponse> getPendingFarmers(Pageable pageable);

    /**
     * Lấy danh sách tất cả Farmer (có thể lọc theo trạng thái duyệt và keyword).
     * @param verificationStatus Lọc theo trạng thái duyệt (tùy chọn).
     * @param keyword Lọc theo từ khóa (tên farmer, tên trang trại - tùy chọn).
     * @param pageable Thông tin phân trang và sắp xếp.
     * @return Trang danh sách Farmer Profile đầy đủ (UserProfileResponse).
     */
    Page<UserProfileResponse> getAllFarmers(VerificationStatus verificationStatus, String keyword, Pageable pageable);


    /**
     * Admin duyệt hồ sơ Farmer.
     * @param userId ID của user (Farmer).
     * @param adminAuth Thông tin admin thực hiện.
//     * @throws ResourceNotFoundException Nếu không tìm thấy Farmer Profile.
//     * @throws BadRequestException Nếu profile không ở trạng thái PENDING.
     */
    void approveFarmer(Long userId, Authentication adminAuth);

    /**
     * Admin từ chối hồ sơ Farmer.
     * @param userId ID của user (Farmer).
     * @param reason Lý do từ chối (tùy chọn).
     * @param adminAuth Thông tin admin thực hiện.
//     * @throws ResourceNotFoundException Nếu không tìm thấy Farmer Profile.
//     * @throws BadRequestException Nếu profile không ở trạng thái PENDING.
//     */
    void rejectFarmer(Long userId, String reason, Authentication adminAuth);

    // Có thể thêm các phương thức khác: xóa user, xem chi tiết business buyer...
}