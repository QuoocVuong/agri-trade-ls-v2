package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.BusinessProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.BusinessProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.BusinessProfileResponse;
import com.yourcompany.agritrade.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritrade.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import com.yourcompany.agritrade.usermanagement.service.BusinessProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessProfileServiceImpl implements BusinessProfileService {

  private final BusinessProfileRepository businessProfileRepository;
  private final UserRepository userRepository;
  private final BusinessProfileMapper businessProfileMapper;
  private final RoleRepository roleRepository;
  private static final String LANG_SON_PROVINCE_CODE = "20"; // Định nghĩa mã tỉnh Lạng Sơn

  @Override
  @Transactional
  public BusinessProfileResponse createOrUpdateBusinessProfile(
      Authentication authentication, BusinessProfileRequest request) {
    User user = getUserFromAuthentication(authentication);

    // Đảm bảo user là Business Buyer
    //        if (!user.getRoles().stream().anyMatch(role -> role.getName() ==
    // RoleType.ROLE_BUSINESS_BUYER)) {
    //            throw new AccessDeniedException("User is not a Business Buyer");
    //        }

    // Kiểm tra tỉnh Lạng Sơn
    //        if (!LANG_SON_PROVINCE_CODE.equals(request.getBusinessProvinceCode())) {
    //            throw new BadRequestException("Business profile province must be Lang Son (" +
    // LANG_SON_PROVINCE_CODE + ").");
    //        }

    // Kiểm tra xem profile đã tồn tại chưa để biết là tạo mới hay cập nhật
    boolean isNewProfile = !businessProfileRepository.existsById(user.getId());

    // Tìm profile hiện có hoặc tạo đối tượng mới
    BusinessProfile profile =
        businessProfileRepository
            .findById(user.getId())
            .orElseGet(
                () -> {
                  BusinessProfile newProfile =
                      businessProfileMapper.requestToBusinessProfile(request);
                  newProfile.setUser(user); // Gán user vào profile mới
                  return newProfile;
                });

    // Nếu profile đã tồn tại (cập nhật)
    if (profile.getUserId() != null) {
      businessProfileMapper.updateBusinessProfileFromRequest(request, profile);
      // Không thay đổi trạng thái duyệt khi user tự cập nhật
      // Trạng thái duyệt chỉ thay đổi bởi Admin
    }
    // Nếu là tạo mới, trạng thái duyệt sẽ là PENDING (mặc định trong DB hoặc set ở đây nếu cần)
    // profile.setVerificationStatus(VerificationStatus.PENDING); // Nếu cần set tường minh

    // Lưu profile (tạo mới hoặc cập nhật)
    BusinessProfile savedProfile = businessProfileRepository.save(profile);

    // *** Quan trọng: Gán thêm vai trò BUSINESS_BUYER nếu là tạo profile mới ***
    // Chỉ gán nếu user chưa có vai trò này
    if (isNewProfile
        && user.getRoles().stream()
            .noneMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER)) {
      Role businessBuyerRole =
          roleRepository
              .findByName(RoleType.ROLE_BUSINESS_BUYER)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Role", "name", RoleType.ROLE_BUSINESS_BUYER.name()));
      user.getRoles().add(businessBuyerRole); // Thêm vào Set roles hiện có
      userRepository.save(user); // Lưu lại user với role mới
    }

    return businessProfileMapper.toBusinessProfileResponse(savedProfile);
  }

  @Override
  @Transactional(readOnly = true)
  public BusinessProfileResponse getBusinessProfile(Long userId) {
    BusinessProfile profile =
        businessProfileRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("BusinessProfile", "userId", userId));

    // Optional: Kiểm tra xem user có đúng là Business Buyer không trước khi trả về
    // if (!profile.getUser().getRoles().stream().anyMatch(role -> role.getName() ==
    // RoleType.ROLE_BUSINESS_BUYER)) {
    //     throw new ResourceNotFoundException("BusinessProfile", "userId", userId); // Hoặc lỗi
    // khác
    // }

    return businessProfileMapper.toBusinessProfileResponse(profile);
  }

  // Helper lấy User từ Authentication (có thể tách ra Util class nếu dùng nhiều)
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }
}
