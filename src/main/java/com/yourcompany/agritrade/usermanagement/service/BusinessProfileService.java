package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.usermanagement.dto.request.BusinessProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.BusinessProfileResponse;
import org.springframework.security.core.Authentication;

public interface BusinessProfileService {

    /**
     * Tạo hoặc cập nhật thông tin profile cho người mua là doanh nghiệp đang đăng nhập.
     * Chỉ user có vai trò ROLE_BUSINESS_BUYER mới thực hiện được.
     * Đảm bảo tỉnh đăng ký kinh doanh là Lạng Sơn.
     *
     * @param authentication Thông tin xác thực của người dùng hiện tại.
     * @param request        Dữ liệu profile cần tạo/cập nhật.
     * @return Thông tin profile đã được lưu.
     */
    BusinessProfileResponse createOrUpdateBusinessProfile(Authentication authentication, BusinessProfileRequest request);

    /**
     * Lấy thông tin profile của một doanh nghiệp dựa trên userId.
     * Có thể dùng để hiển thị công khai hoặc cho các mục đích nội bộ.
     *
     * @param userId ID của người dùng (doanh nghiệp).
     * @return Thông tin profile của doanh nghiệp.

     */
    BusinessProfileResponse getBusinessProfile(Long userId);

    // Có thể thêm các phương thức khác nếu cần (ví dụ: cho Admin)
}