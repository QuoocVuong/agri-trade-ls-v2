package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.*;
import com.yourcompany.agritrade.usermanagement.repository.specification.FarmerProfileSpecification;
import com.yourcompany.agritrade.usermanagement.repository.specification.UserSpecification;
import com.yourcompany.agritrade.usermanagement.service.AdminUserService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final FarmerProfileRepository farmerProfileRepository;
  private final BusinessProfileRepository businessProfileRepository;
  private final UserMapper userMapper;
  private final FarmerProfileMapper farmerProfileMapper;
  private final BusinessProfileMapper businessProfileMapper;
  private final NotificationService notificationService; // Inject NotificationService

  @Override
  @Transactional(readOnly = true)
  public Page<UserResponse> getAllUsers(
      Pageable pageable, RoleType roleName, String keyword, Boolean isActive) {
    // Sử dụng Specification để tạo query động
    Specification<User> spec =
        Specification.where(UserSpecification.hasRole(roleName))
            .and(UserSpecification.hasKeyword(keyword))
            .and(UserSpecification.isActive(isActive))
            .and(UserSpecification.isNotDeleted()); // Luôn loại trừ user đã xóa mềm

    return userRepository.findAll(spec, pageable).map(userMapper::toUserResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getUserProfileById(Long userId) {
    // findById của repo đã tự lọc is_deleted=false nhờ @Where
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

    // Lấy profile tương ứng (giống logic trong UserServiceImpl)
    UserProfileResponse response = userMapper.toUserProfileResponse(user);
    if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_FARMER)) {
      farmerProfileRepository
          .findById(user.getId())
          .ifPresent(
              profile ->
                  response.setFarmerProfile(farmerProfileMapper.toFarmerProfileResponse(profile)));
    } else if (user.getRoles().stream()
        .anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER)) {
      businessProfileRepository
          .findById(user.getId())
          .ifPresent(
              profile ->
                  response.setBusinessProfile(
                      businessProfileMapper.toBusinessProfileResponse(profile)));
    }
    return response;
  }

  @Override
  @Transactional
  public UserResponse updateUserStatus(Long userId, boolean isActive, Authentication adminAuth) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

    if (user.isActive() == isActive) {
      log.warn(
          "Admin {} attempted to set the same status ({}) for user {}",
          adminAuth.getName(),
          isActive,
          userId);
      return userMapper.toUserResponse(user); // Trả về trạng thái hiện tại nếu không đổi
    }

    user.setActive(isActive);
    User updatedUser = userRepository.save(user);
    log.info("User {} status updated to {} by admin {}", userId, isActive, adminAuth.getName());

    // Gửi thông báo cho người dùng
    notificationService.sendAccountStatusUpdateNotification(updatedUser, isActive);

    return userMapper.toUserResponse(updatedUser);
  }

  @Override
  @Transactional
  public UserResponse updateUserRoles(
      Long userId, Set<RoleType> roleNames, Authentication adminAuth) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

    Set<Role> newRoles = new HashSet<>();
    if (roleNames != null && !roleNames.isEmpty()) {
      for (RoleType roleName : roleNames) {
        Role role =
            roleRepository
                .findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName.name()));
        newRoles.add(role);
      }
    } // Cho phép xóa hết role nếu truyền vào Set rỗng

    user.setRoles(newRoles);
    User updatedUser = userRepository.save(user);
    log.info("User {} roles updated to {} by admin {}", userId, roleNames, adminAuth.getName());

    // Gửi thông báo cho người dùng
    notificationService.sendRolesUpdateNotification(updatedUser);

    return userMapper.toUserResponse(updatedUser);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<UserProfileResponse> getPendingFarmers(Pageable pageable) {
    // Query FarmerProfile có status PENDING, sau đó lấy User tương ứng
    // Cần join fetch user để lấy đủ thông tin cho UserProfileResponse
    Page<FarmerProfile> pendingProfiles =
        farmerProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, pageable);

    // Map sang UserProfileResponse
    return pendingProfiles.map(
        fp -> {
          UserProfileResponse upr =
              userMapper.toUserProfileResponse(fp.getUser()); // Map user trước
          upr.setFarmerProfile(
              farmerProfileMapper.toFarmerProfileResponse(fp)); // Gán farmer profile
          return upr;
        });
  }

  //    @Override
  //    @Transactional(readOnly = true)
  //    public Page<UserProfileResponse> getAllFarmers(VerificationStatus verificationStatus, String
  // keyword, Pageable pageable) {
  //        // Dùng Specification để lọc User có role FARMER và các điều kiện khác
  //        Specification<User> spec =
  // Specification.where(UserSpecification.hasRole(RoleType.ROLE_FARMER))
  //                .and(UserSpecification.hasKeyword(keyword))
  //                .and(UserSpecification.hasFarmerVerificationStatus(verificationStatus)) // Thêm
  // spec này
  //                .and(UserSpecification.isNotDeleted());
  //
  //        Page<User> farmerUsers = userRepository.findAll(spec, pageable);
  //
  //        // Map sang UserProfileResponse (cần lấy cả FarmerProfile)
  //        return farmerUsers.map(user -> {
  //            UserProfileResponse upr = userMapper.toUserProfileResponse(user);
  //            // Lấy FarmerProfile (có thể gây N+1 nếu không tối ưu)
  //            farmerProfileRepository.findById(user.getId()).ifPresent(profile ->
  //                    upr.setFarmerProfile(farmerProfileMapper.toFarmerProfileResponse(profile))
  //            );
  //            return upr;
  //        });
  //    }
  @Override
  @Transactional(readOnly = true)
  public Page<UserProfileResponse> getAllFarmers(
      VerificationStatus verificationStatus, String keyword, Pageable pageable) {
    // *** Query từ FarmerProfileRepository ***
    Specification<FarmerProfile> spec =
        Specification.where(FarmerProfileSpecification.hasVerificationStatus(verificationStatus))
            .and(
                FarmerProfileSpecification.userHasKeyword(
                    keyword)); // *** Dùng Spec của FarmerProfile ***

    // Cần JpaSpecificationExecutor trên FarmerProfileRepository
    Page<FarmerProfile> farmerProfilePage = farmerProfileRepository.findAll(spec, pageable);

    // Map sang UserProfileResponse
    return farmerProfilePage.map(
        fp -> {
          UserProfileResponse upr =
              userMapper.toUserProfileResponse(fp.getUser()); // Map user trước
          upr.setFarmerProfile(
              farmerProfileMapper.toFarmerProfileResponse(fp)); // Gán farmer profile
          return upr;
        });
  }

  @Override
  @Transactional
  public void approveFarmer(Long userId, Authentication adminAuth) {
    User admin = getUserFromAuthentication(adminAuth); // Lấy thông tin Admin
    FarmerProfile profile =
        farmerProfileRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile", "userId", userId));

    if (profile.getVerificationStatus() != VerificationStatus.PENDING) {
      throw new BadRequestException("Farmer profile is not in PENDING status.");
    }

    profile.setVerificationStatus(VerificationStatus.VERIFIED);
    profile.setVerifiedAt(LocalDateTime.now());
    profile.setVerifiedBy(admin); // Lưu lại admin nào đã duyệt
    farmerProfileRepository.save(profile);

    // Kích hoạt tài khoản User nếu chưa active
    User farmerUser = profile.getUser();
    if (!farmerUser.isActive()) {
      farmerUser.setActive(true);
      userRepository.save(farmerUser);
    }

    log.info("Farmer profile {} approved by admin {}", userId, admin.getEmail());
    // Gửi thông báo cho Farmer
    notificationService.sendFarmerProfileApprovedNotification(profile); // Cần tạo hàm này
  }

  @Override
  @Transactional
  public void rejectFarmer(Long userId, String reason, Authentication adminAuth) {
    User admin = getUserFromAuthentication(adminAuth);
    FarmerProfile profile =
        farmerProfileRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile", "userId", userId));

    if (profile.getVerificationStatus() != VerificationStatus.PENDING) {
      throw new BadRequestException("Farmer profile is not in PENDING status.");
    }

    profile.setVerificationStatus(VerificationStatus.REJECTED);
    // profile.setRejectReason(reason); // Cần thêm cột reject_reason vào DB và Entity
    profile.setVerifiedAt(LocalDateTime.now()); // Vẫn lưu thời gian xử lý
    profile.setVerifiedBy(admin);
    farmerProfileRepository.save(profile);

    // Không kích hoạt tài khoản User
    log.info(
        "Farmer profile {} rejected by admin {}. Reason: {}", userId, admin.getEmail(), reason);
    // Gửi thông báo cho Farmer
    notificationService.sendFarmerProfileRejectedNotification(profile, reason); // Cần tạo hàm này
  }

  // Helper method (copy từ UserServiceImpl hoặc tách ra Util)
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () ->
                new UsernameNotFoundException(
                    "Admin User not found with email: " + email)); // Sửa thông báo lỗi
  }
}

// *** Cần tạo UserSpecification.java ***
// (File: usermanagement/repository/specification/UserSpecification.java - Mới)
/*

*/
