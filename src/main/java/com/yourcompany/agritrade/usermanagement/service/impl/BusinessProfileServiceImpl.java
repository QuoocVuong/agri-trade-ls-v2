package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.util.SecurityUtils;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessProfileServiceImpl implements BusinessProfileService {

  private final BusinessProfileRepository businessProfileRepository;
  private final UserRepository userRepository;
  private final BusinessProfileMapper businessProfileMapper;
  private final RoleRepository roleRepository;

  @Override
  @Transactional
  public BusinessProfileResponse createOrUpdateBusinessProfile(
      Authentication authentication, BusinessProfileRequest request) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();

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
    }

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

    return businessProfileMapper.toBusinessProfileResponse(profile);
  }
}
