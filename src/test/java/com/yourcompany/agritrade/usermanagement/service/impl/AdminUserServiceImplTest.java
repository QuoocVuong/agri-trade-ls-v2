package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private FarmerProfileRepository farmerProfileRepository;
  @Mock private BusinessProfileRepository businessProfileRepository;
  @Mock private UserMapper userMapper;
  @Mock private FarmerProfileMapper farmerProfileMapper;
  @Mock private BusinessProfileMapper businessProfileMapper;
  @Mock private NotificationService notificationService;
  @Mock private Authentication adminAuth;

  // Thêm MockedStatic để quản lý mock cho lớp tiện ích SecurityUtils
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private AdminUserServiceImpl adminUserService;

  private User testUser;
  private User adminUser;
  private Role farmerRole;
  private Role consumerRole;
  private FarmerProfile farmerProfile;

  @BeforeEach
  void setUp() {
    // Khởi tạo mock static cho SecurityUtils trước mỗi test
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    adminUser = new User();
    adminUser.setId(1L);
    adminUser.setEmail("admin@example.com");
    adminUser.setFullName("Admin User");

    testUser = new User();
    testUser.setId(2L);
    testUser.setEmail("test@example.com");
    testUser.setFullName("Test User");
    testUser.setActive(true);

    farmerRole = new Role(RoleType.ROLE_FARMER);
    consumerRole = new Role(RoleType.ROLE_CONSUMER);

    farmerProfile = new FarmerProfile();
    farmerProfile.setUserId(testUser.getId());
    farmerProfile.setUser(testUser);
    farmerProfile.setVerificationStatus(VerificationStatus.PENDING);

    // Sử dụng lenient() vì không phải tất cả các test đều dùng adminAuth.getName()
    lenient().when(adminAuth.getName()).thenReturn(adminUser.getEmail());
  }

  @AfterEach
  void tearDown() {
    // Đóng mock static sau mỗi test để tránh ảnh hưởng đến các test khác
    mockedSecurityUtils.close();
  }

  @Nested
  @DisplayName("User Management Tests")
  class UserManagement {
    @Test
    @DisplayName("Get All Users - With Filters - Success")
    void getAllUsers_withFilters_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<User> userPage = new PageImpl<>(List.of(testUser));
      when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);
      when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());

      Page<UserResponse> result =
          adminUserService.getAllUsers(pageable, RoleType.ROLE_CONSUMER, "test", true);

      assertNotNull(result);
      assertEquals(1, result.getContent().size());
      verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Get User Profile By Id - Success")
    void getUserProfileById_success() {
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(userMapper.toUserProfileResponse(testUser)).thenReturn(new UserProfileResponse());

      UserProfileResponse result = adminUserService.getUserProfileById(testUser.getId());

      assertNotNull(result);
      verify(userRepository).findById(testUser.getId());
    }

    @Test
    @DisplayName("Update User Status - Success")
    void updateUserStatus_success() {
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(userRepository.save(any(User.class))).thenReturn(testUser);
      when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());
      doNothing()
          .when(notificationService)
          .sendAccountStatusUpdateNotification(any(User.class), eq(false));

      adminUserService.updateUserStatus(testUser.getId(), false, adminAuth);

      assertFalse(testUser.isActive());
      verify(userRepository).save(testUser);
      verify(notificationService).sendAccountStatusUpdateNotification(testUser, false);
    }

    @Test
    @DisplayName("Update User Status - No Change - Returns Current")
    void updateUserStatus_noChange_returnsCurrent() {
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(userMapper.toUserResponse(testUser)).thenReturn(new UserResponse());

      adminUserService.updateUserStatus(
          testUser.getId(), true, adminAuth); // Status is already true

      verify(userRepository, never()).save(any(User.class));
      verify(notificationService, never()).sendAccountStatusUpdateNotification(any(), anyBoolean());
    }

    @Test
    @DisplayName("Update User Roles - Success")
    void updateUserRoles_success() {
      Set<RoleType> newRoleTypes = Set.of(RoleType.ROLE_FARMER, RoleType.ROLE_CONSUMER);
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(roleRepository.findByName(RoleType.ROLE_FARMER)).thenReturn(Optional.of(farmerRole));
      when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
      when(userRepository.save(any(User.class))).thenReturn(testUser);
      when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());
      doNothing().when(notificationService).sendRolesUpdateNotification(any(User.class));

      adminUserService.updateUserRoles(testUser.getId(), newRoleTypes, adminAuth);

      assertEquals(2, testUser.getRoles().size());
      verify(userRepository).save(testUser);
      verify(notificationService).sendRolesUpdateNotification(testUser);
    }
  }

  @Nested
  @DisplayName("Farmer Approval Tests")
  class FarmerApprovalTests {

    @BeforeEach
    void farmerApprovalSetup() {
      // Mock SecurityUtils để trả về adminUser cho các test trong nhóm này
      mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(adminUser);
    }

    @Test
    @DisplayName("Get Pending Farmers - Success")
    void getPendingFarmers_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<FarmerProfile> profilePage = new PageImpl<>(List.of(farmerProfile));
      when(farmerProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, pageable))
          .thenReturn(profilePage);
      when(userMapper.toUserProfileResponse(any(User.class))).thenReturn(new UserProfileResponse());
      when(farmerProfileMapper.toFarmerProfileResponse(any(FarmerProfile.class)))
          .thenReturn(new FarmerProfileResponse());

      Page<UserProfileResponse> result = adminUserService.getPendingFarmers(pageable);

      assertNotNull(result);
      assertEquals(1, result.getContent().size());
      verify(farmerProfileRepository)
          .findByVerificationStatus(VerificationStatus.PENDING, pageable);
    }

    @Test
    @DisplayName("Get All Farmers - With Filters - Success")
    void getAllFarmers_withFilters_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<FarmerProfile> profilePage = new PageImpl<>(List.of(farmerProfile));
      when(farmerProfileRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(profilePage);
      when(userMapper.toUserProfileResponse(any(User.class))).thenReturn(new UserProfileResponse());
      when(farmerProfileMapper.toFarmerProfileResponse(any(FarmerProfile.class)))
          .thenReturn(new FarmerProfileResponse());

      Page<UserProfileResponse> result =
          adminUserService.getAllFarmers(VerificationStatus.PENDING, "test", pageable);

      assertNotNull(result);
      assertEquals(1, result.getContent().size());
      verify(farmerProfileRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Approve Farmer - Success")
    void approveFarmer_success() {
      testUser.setActive(false); // Giả lập user chưa active
      when(farmerProfileRepository.findById(testUser.getId()))
          .thenReturn(Optional.of(farmerProfile));
      when(farmerProfileRepository.save(any(FarmerProfile.class))).thenReturn(farmerProfile);
      when(userRepository.save(any(User.class))).thenReturn(testUser);
      doNothing()
          .when(notificationService)
          .sendFarmerProfileApprovedNotification(any(FarmerProfile.class));

      adminUserService.approveFarmer(testUser.getId(), adminAuth);

      assertEquals(VerificationStatus.VERIFIED, farmerProfile.getVerificationStatus());
      assertNotNull(farmerProfile.getVerifiedAt());
      assertEquals(adminUser, farmerProfile.getVerifiedBy());
      assertTrue(testUser.isActive()); // User should be activated

      verify(farmerProfileRepository).save(farmerProfile);
      verify(userRepository).save(testUser);
      verify(notificationService).sendFarmerProfileApprovedNotification(farmerProfile);
    }

    @Test
    @DisplayName("Approve Farmer - Profile Not Found - Throws ResourceNotFoundException")
    void approveFarmer_profileNotFound_throwsResourceNotFound() {
      when(farmerProfileRepository.findById(testUser.getId())).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> adminUserService.approveFarmer(testUser.getId(), adminAuth));
    }

    @Test
    @DisplayName("Approve Farmer - Profile Not Pending - Throws BadRequestException")
    void approveFarmer_profileNotPending_throwsBadRequest() {
      farmerProfile.setVerificationStatus(
          VerificationStatus.VERIFIED); // Set to a non-pending status
      when(farmerProfileRepository.findById(testUser.getId()))
          .thenReturn(Optional.of(farmerProfile));

      assertThrows(
          BadRequestException.class,
          () -> adminUserService.approveFarmer(testUser.getId(), adminAuth));
    }

    @Test
    @DisplayName("Reject Farmer - Success")
    void rejectFarmer_success() {
      String reason = "Information mismatch";
      when(farmerProfileRepository.findById(testUser.getId()))
          .thenReturn(Optional.of(farmerProfile));
      when(farmerProfileRepository.save(any(FarmerProfile.class))).thenReturn(farmerProfile);
      doNothing()
          .when(notificationService)
          .sendFarmerProfileRejectedNotification(any(FarmerProfile.class), eq(reason));

      adminUserService.rejectFarmer(testUser.getId(), reason, adminAuth);

      assertEquals(VerificationStatus.REJECTED, farmerProfile.getVerificationStatus());
      assertNotNull(farmerProfile.getVerifiedAt());
      assertEquals(adminUser, farmerProfile.getVerifiedBy());

      verify(farmerProfileRepository).save(farmerProfile);
      verify(userRepository, never()).save(any(User.class)); // User should not be saved/activated
      verify(notificationService).sendFarmerProfileRejectedNotification(farmerProfile, reason);
    }
  }

  @Test
  @DisplayName("Approve Farmer - Admin Not Found - Throws UsernameNotFoundException")
  void approveFarmer_adminNotFound_throwsUsernameNotFound() {
    // Mock SecurityUtils để nó ném lỗi, mô phỏng đúng kịch bản admin không tồn tại
    mockedSecurityUtils
        .when(SecurityUtils::getCurrentAuthenticatedUser)
        .thenThrow(new UsernameNotFoundException("Admin not found"));

    // Không cần mock farmerProfileRepository.findById vì service sẽ ném lỗi trước khi gọi đến nó
    // Nhưng để chắc chắn, ta vẫn có thể mock để tránh NullPointerException nếu logic thay đổi
    lenient()
        .when(farmerProfileRepository.findById(testUser.getId()))
        .thenReturn(Optional.of(farmerProfile));

    // Act & Assert
    assertThrows(
        UsernameNotFoundException.class,
        () -> adminUserService.approveFarmer(testUser.getId(), adminAuth));
  }
}
