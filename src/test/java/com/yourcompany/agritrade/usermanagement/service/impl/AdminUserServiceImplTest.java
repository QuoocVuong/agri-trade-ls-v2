package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.BusinessProfileResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;


import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
    @Mock private Authentication adminAuthentication;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private User adminUser, testUser, testFarmer;
    private Role adminRole, farmerRole, consumerRole;
    private FarmerProfile farmerProfileEntity;
    private UserResponse userResponseDto;
    private UserProfileResponse userProfileResponseDto;
    private FarmerProfileResponse farmerProfileResponseDto;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@example.com");
        adminUser.setFullName("Admin User");

        testUser = new User();
        testUser.setId(2L);
        testUser.setEmail("user@example.com");
        testUser.setFullName("Regular User");
        testUser.setActive(true);

        testFarmer = new User();
        testFarmer.setId(3L);
        testFarmer.setEmail("farmer@example.com");
        testFarmer.setFullName("Farmer User");
        testFarmer.setActive(false); // Giả sử farmer mới đăng ký, chưa active

        adminRole = new Role(RoleType.ROLE_ADMIN); adminRole.setId(100);
        farmerRole = new Role(RoleType.ROLE_FARMER); farmerRole.setId(101);
        consumerRole = new Role(RoleType.ROLE_CONSUMER); consumerRole.setId(102);

        testUser.setRoles(new HashSet<>(Set.of(consumerRole)));
        testFarmer.setRoles(new HashSet<>(Set.of(farmerRole)));


        farmerProfileEntity = new FarmerProfile();
        farmerProfileEntity.setUserId(testFarmer.getId());
        farmerProfileEntity.setUser(testFarmer);
        farmerProfileEntity.setFarmName("Green Acres");
        farmerProfileEntity.setVerificationStatus(VerificationStatus.PENDING);
        testFarmer.setFarmerProfile(farmerProfileEntity);


        userResponseDto = new UserResponse();
        userResponseDto.setId(testUser.getId());
        userResponseDto.setEmail(testUser.getEmail());
        // ...

        userProfileResponseDto = new UserProfileResponse();
        userProfileResponseDto.setId(testUser.getId());
        // ...

        farmerProfileResponseDto = new FarmerProfileResponse();
        farmerProfileResponseDto.setUserId(testFarmer.getId());
        // ...

        lenient().when(adminAuthentication.getName()).thenReturn(adminUser.getEmail());
        lenient().when(adminAuthentication.isAuthenticated()).thenReturn(true); // <<< THÊM DÒNG NÀY
        lenient().when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        // Bạn cũng có thể cần mock getPrincipal() nếu logic getUserFromAuthentication thay đổi
        lenient().when(adminAuthentication.getPrincipal()).thenReturn(adminUser); // Hoặc một UserDetails object
        
    }

    @Nested
    @DisplayName("Get User Information Tests")
    class GetUserInformationTests {
        @Test
        @DisplayName("Get All Users - Success with Filters")
        void getAllUsers_withFilters_shouldReturnFilteredPage() {
            Pageable pageable = PageRequest.of(0, 10);
            RoleType roleFilter = RoleType.ROLE_FARMER;
            String keywordFilter = "Farmer";
            Boolean activeFilter = true;
            Page<User> userPage = new PageImpl<>(List.of(testFarmer), pageable, 1);

            when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);
            when(userMapper.toUserResponse(testFarmer)).thenReturn(userResponseDto); // Giả sử map ra DTO này

            Page<UserResponse> result = adminUserService.getAllUsers(pageable, roleFilter, keywordFilter, activeFilter);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(userResponseDto.getEmail(), result.getContent().get(0).getEmail());
            verify(userRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("Get User Profile By Id - Farmer User - Success")
        void getUserProfileById_farmerUser_success() {
            when(userRepository.findById(testFarmer.getId())).thenReturn(Optional.of(testFarmer));
            when(userMapper.toUserProfileResponse(testFarmer)).thenReturn(userProfileResponseDto); // Base mapping
            when(farmerProfileRepository.findById(testFarmer.getId())).thenReturn(Optional.of(farmerProfileEntity));
            when(farmerProfileMapper.toFarmerProfileResponse(farmerProfileEntity)).thenReturn(farmerProfileResponseDto);

            UserProfileResponse result = adminUserService.getUserProfileById(testFarmer.getId());

            assertNotNull(result);
            assertEquals(userProfileResponseDto.getId(), result.getId());
            assertNotNull(result.getFarmerProfile());
            assertEquals(farmerProfileResponseDto.getUserId(), result.getFarmerProfile().getUserId());
        }

        @Test
        @DisplayName("Get User Profile By Id - User Not Found - Throws ResourceNotFoundException")
        void getUserProfileById_userNotFound_throwsResourceNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> adminUserService.getUserProfileById(99L));
        }
    }

    @Nested
    @DisplayName("Update User Tests")
    class UpdateUserTests {
        @Test
        @DisplayName("Update User Status - Success")
        void updateUserStatus_success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto);
            doNothing().when(notificationService).sendAccountStatusUpdateNotification(any(User.class), anyBoolean());

            UserResponse result = adminUserService.updateUserStatus(testUser.getId(), false, adminAuthentication); // Deactivate

            assertNotNull(result);
            assertFalse(testUser.isActive());
            verify(userRepository).save(testUser);
            verify(notificationService).sendAccountStatusUpdateNotification(testUser, false);
        }

        @Test
        @DisplayName("Update User Status - Same Status - Should Return Current and Not Save or Notify")
        void updateUserStatus_sameStatus_shouldReturnCurrentAndNotSaveOrNotify() {
            testUser.setActive(true); // Current status is true
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);

            UserResponse result = adminUserService.updateUserStatus(testUser.getId(), true, adminAuthentication); // Request to set true again

            assertNotNull(result);
            assertTrue(testUser.isActive());
            verify(userRepository, never()).save(any(User.class));
            verify(notificationService, never()).sendAccountStatusUpdateNotification(any(User.class), anyBoolean());
        }


        @Test
        @DisplayName("Update User Roles - Success")
        void updateUserRoles_success() {
            Set<RoleType> newRoleTypes = Set.of(RoleType.ROLE_FARMER, RoleType.ROLE_CONSUMER);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(roleRepository.findByName(RoleType.ROLE_FARMER)).thenReturn(Optional.of(farmerRole));
            when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto); // userResponseDto cần được cập nhật roles
            doNothing().when(notificationService).sendRolesUpdateNotification(any(User.class));

            UserResponse result = adminUserService.updateUserRoles(testUser.getId(), newRoleTypes, adminAuthentication);

            assertNotNull(result);
            assertEquals(2, testUser.getRoles().size());
            assertTrue(testUser.getRoles().contains(farmerRole));
            assertTrue(testUser.getRoles().contains(consumerRole));
            verify(notificationService).sendRolesUpdateNotification(testUser);
        }

        @Test
        @DisplayName("Update User Roles - Role Not Found - Throws ResourceNotFoundException")
        void updateUserRoles_roleNotFound_throwsResourceNotFound() {
            Set<RoleType> newRoleTypes = Set.of(RoleType.ROLE_ADMIN); // Giả sử ROLE_ADMIN không có trong DB
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(roleRepository.findByName(RoleType.ROLE_ADMIN)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> adminUserService.updateUserRoles(testUser.getId(), newRoleTypes, adminAuthentication));
        }
    }

    @Nested
    @DisplayName("Farmer Approval Tests")
    class FarmerApprovalTests {
        @Test
        @DisplayName("Get Pending Farmers - Success")
        void getPendingFarmers_success() {
            Pageable pageable = PageRequest.of(0, 5);
            List<FarmerProfile> pendingList = List.of(farmerProfileEntity);
            Page<FarmerProfile> pendingPage = new PageImpl<>(pendingList, pageable, pendingList.size());

            when(farmerProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, pageable))
                    .thenReturn(pendingPage);
            // Mock mapper calls
            when(userMapper.toUserProfileResponse(testFarmer)).thenReturn(userProfileResponseDto);
            when(farmerProfileMapper.toFarmerProfileResponse(farmerProfileEntity)).thenReturn(farmerProfileResponseDto);


            Page<UserProfileResponse> result = adminUserService.getPendingFarmers(pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(userProfileResponseDto.getId(), result.getContent().get(0).getId());
            assertNotNull(result.getContent().get(0).getFarmerProfile());
        }

        @Test
        @DisplayName("Get All Farmers - With Filters - Success")
        void getAllFarmers_withFilters_success() {
            Pageable pageable = PageRequest.of(0, 10);
            VerificationStatus statusFilter = VerificationStatus.VERIFIED;
            String keywordFilter = "Green";
            farmerProfileEntity.setVerificationStatus(VerificationStatus.VERIFIED); // Set cho khớp filter
            Page<FarmerProfile> farmerProfilePage = new PageImpl<>(List.of(farmerProfileEntity), pageable, 1);

            when(farmerProfileRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(farmerProfilePage);
            when(userMapper.toUserProfileResponse(testFarmer)).thenReturn(userProfileResponseDto);
            when(farmerProfileMapper.toFarmerProfileResponse(farmerProfileEntity)).thenReturn(farmerProfileResponseDto);

            Page<UserProfileResponse> result = adminUserService.getAllFarmers(statusFilter, keywordFilter, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(farmerProfileRepository).findAll(any(Specification.class), eq(pageable));
        }


        @Test
        @DisplayName("Approve Farmer - Success")
        void approveFarmer_success() {
            when(farmerProfileRepository.findById(testFarmer.getId())).thenReturn(Optional.of(farmerProfileEntity));
            when(farmerProfileRepository.save(any(FarmerProfile.class))).thenReturn(farmerProfileEntity);
            when(userRepository.save(any(User.class))).thenReturn(testFarmer); // Farmer user được active
            doNothing().when(notificationService).sendFarmerProfileApprovedNotification(any(FarmerProfile.class));

            adminUserService.approveFarmer(testFarmer.getId(), adminAuthentication);

            assertEquals(VerificationStatus.VERIFIED, farmerProfileEntity.getVerificationStatus());
            assertNotNull(farmerProfileEntity.getVerifiedAt());
            assertEquals(adminUser, farmerProfileEntity.getVerifiedBy()); // Kiểm tra admin duyệt
            assertTrue(testFarmer.isActive());
            verify(notificationService).sendFarmerProfileApprovedNotification(farmerProfileEntity);
        }

        @Test
        @DisplayName("Approve Farmer - Profile Not Pending - Throws BadRequestException")
        void approveFarmer_profileNotPending_throwsBadRequest() {
            farmerProfileEntity.setVerificationStatus(VerificationStatus.VERIFIED); // Profile không phải PENDING
            // Đảm bảo adminAuthentication được mock đúng với isAuthenticated() = true
            // (đã làm trong setUp() hoặc mock cụ thể ở đây nếu cần)
            when(adminAuthentication.isAuthenticated()).thenReturn(true); // Có thể thêm tường minh ở đây
            when(adminAuthentication.getName()).thenReturn(adminUser.getEmail());
            when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));


            when(farmerProfileRepository.findById(testFarmer.getId())).thenReturn(Optional.of(farmerProfileEntity));

            assertThrows(BadRequestException.class,
                    () -> adminUserService.approveFarmer(testFarmer.getId(), adminAuthentication));
        }

        @Test
        @DisplayName("Reject Farmer - Success")
        void rejectFarmer_success() {
            String reason = "Incomplete documents";
            when(farmerProfileRepository.findById(testFarmer.getId())).thenReturn(Optional.of(farmerProfileEntity));
            when(farmerProfileRepository.save(any(FarmerProfile.class))).thenReturn(farmerProfileEntity);
            doNothing().when(notificationService).sendFarmerProfileRejectedNotification(any(FarmerProfile.class), anyString());

            adminUserService.rejectFarmer(testFarmer.getId(), reason, adminAuthentication);

            assertEquals(VerificationStatus.REJECTED, farmerProfileEntity.getVerificationStatus());
            assertNotNull(farmerProfileEntity.getVerifiedAt());
            assertEquals(adminUser, farmerProfileEntity.getVerifiedBy());
            assertFalse(testFarmer.isActive()); // User không được active
            verify(notificationService).sendFarmerProfileRejectedNotification(farmerProfileEntity, reason);
        }
    }

    @Test
    @DisplayName("Get User From Authentication - Admin User Not Found - Throws UsernameNotFoundException")
    void getUserFromAuthentication_adminNotFound_throwsUsernameNotFound() {
        String nonExistentAdminEmail = "nonexistentadmin@example.com";
        // Mock Authentication cho một admin không tồn tại
        Authentication mockAuthNonExistent = mock(Authentication.class);
        when(mockAuthNonExistent.getName()).thenReturn(nonExistentAdminEmail);
        when(mockAuthNonExistent.isAuthenticated()).thenReturn(true); // <<< QUAN TRỌNG
        when(mockAuthNonExistent.getPrincipal()).thenReturn(nonExistentAdminEmail); // Hoặc một UserDetails giả

        when(userRepository.findByEmail(nonExistentAdminEmail)).thenReturn(Optional.empty()); // Admin không tồn tại trong DB

        // Gọi một phương thức bất kỳ trong service mà sử dụng getUserFromAuthentication với mockAuthNonExistent
        assertThrows(UsernameNotFoundException.class,
                () -> adminUserService.approveFarmer(testFarmer.getId(), mockAuthNonExistent));
    }
}