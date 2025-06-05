package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.FarmerProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerProfileResponse;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import com.yourcompany.agritrade.usermanagement.service.FarmerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FarmerProfileServiceImpl implements FarmerProfileService {

  private final FarmerProfileRepository farmerProfileRepository;
  private final UserRepository userRepository;
  private final FarmerProfileMapper farmerProfileMapper;
  private final RoleRepository roleRepository;
  private static final String LANG_SON_PROVINCE_CODE = "20";

  @Override
  @Transactional
  public FarmerProfileResponse createOrUpdateFarmerProfile(
      Authentication authentication, FarmerProfileRequest request) {
    User user = getUserFromAuthentication(authentication);



    boolean isNewProfile =
        !farmerProfileRepository.existsById(user.getId()); // Kiểm tra xem có phải tạo mới không

    FarmerProfile profile =
        farmerProfileRepository
            .findById(user.getId())
            .orElseGet(
                () -> {
                  FarmerProfile newProfile = farmerProfileMapper.requestToFarmerProfile(request);
                  newProfile.setUser(user);
                  return newProfile;
                });

    if (profile.getUserId() != null) { // Nếu là cập nhật
      farmerProfileMapper.updateFarmerProfileFromRequest(request, profile);
      // Giữ nguyên trạng thái duyệt nếu không phải tạo mới
      profile.setVerificationStatus(
          isNewProfile ? VerificationStatus.PENDING : profile.getVerificationStatus());
    } else { // Nếu là tạo mới
      profile.setVerificationStatus(VerificationStatus.PENDING);
    }

    FarmerProfile savedProfile = farmerProfileRepository.save(profile);

    // *** Quan trọng: Gán thêm vai trò FARMER nếu là tạo profile mới ***
    if (isNewProfile) {
      Role farmerRole =
          roleRepository
              .findByName(RoleType.ROLE_FARMER)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Role", "name", RoleType.ROLE_FARMER.name()));
      user.getRoles().add(farmerRole); // Thêm vào Set roles hiện có
      userRepository.save(user); // Lưu lại user với role mới
    }

    return farmerProfileMapper.toFarmerProfileResponse(savedProfile);
  }

  @Override
  @Transactional(readOnly = true)
  public FarmerProfileResponse getFarmerProfile(Long userId) {
    FarmerProfile profile =
        farmerProfileRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile", "userId", userId));
    // Có thể thêm kiểm tra xem user có phải là farmer không nếu cần
    return farmerProfileMapper.toFarmerProfileResponse(profile);
  }

  // Helper lấy User từ Authentication
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
