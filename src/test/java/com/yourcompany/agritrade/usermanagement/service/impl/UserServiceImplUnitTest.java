package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.config.properties.JwtProperties;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerSummaryMapper;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserMapper userMapper;
  @Mock private FarmerProfileRepository farmerProfileRepository;
  @Mock private BusinessProfileRepository businessProfileRepository;
  @Mock private FarmerProfileMapper farmerProfileMapper;
  @Mock private BusinessProfileMapper businessProfileMapper;
  @Mock private EmailService emailService;
  @Mock private FarmerSummaryMapper farmerSummaryMapper;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtProperties jwtProperties;
  @Mock private NotificationService notificationService;
  @Mock private Authentication authentication; // Mock cho các phương thức cần Authentication

  // Mocks cho GoogleIdTokenVerifier
  @Mock private GoogleIdTokenVerifier.Builder googleIdTokenVerifierBuilder;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private GoogleIdToken googleIdToken;
  @Mock private GoogleIdToken.Payload googleIdTokenPayload;

  @InjectMocks private UserServiceImpl userService;

  private UserRegistrationRequest registrationRequest;
  private User testUser, farmerUser;
  private User testUser1, farmerProfileEntity1;
  private Role consumerRole, farmerRole;
  private UserResponse userResponseDto;
  private UserProfileResponse userProfileResponseDto;
  private FarmerProfile farmerProfileEntity;

  @BeforeEach
  void setUp() {
    // Gán giá trị cho @Value fields
    ReflectionTestUtils.setField(userService, "frontendUrl", "http://localhost:4200");
    ReflectionTestUtils.setField(userService, "googleClientId", "test-google-client-id");

    registrationRequest = new UserRegistrationRequest();
    registrationRequest.setEmail("test@example.com");
    registrationRequest.setPassword("password123");
    registrationRequest.setFullName("Test User");
    registrationRequest.setPhoneNumber("0123456789");

    consumerRole = new Role(RoleType.ROLE_CONSUMER);
    consumerRole.setId(1);
    farmerRole = new Role(RoleType.ROLE_FARMER);
    farmerRole.setId(2);

    testUser =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .fullName("Test User")
            .passwordHash("encodedPassword")
            .isActive(true)
            .roles(new HashSet<>(Set.of(consumerRole)))
            .provider("LOCAL")
            .build();

    farmerUser =
        User.builder()
            .id(2L)
            .email("farmer@example.com")
            .fullName("Farmer User")
            .passwordHash("encodedPassword")
            .isActive(true)
            .roles(new HashSet<>(Set.of(farmerRole)))
            .provider("LOCAL")
            .followerCount(10)
            .build();

    farmerProfileEntity = new FarmerProfile();
    farmerProfileEntity.setUserId(farmerUser.getId());
    farmerProfileEntity.setUser(farmerUser);
    farmerProfileEntity.setFarmName("Green Farm");
    farmerProfileEntity.setProvinceCode("20");
    farmerProfileEntity.setVerificationStatus(VerificationStatus.VERIFIED);
    farmerUser.setFarmerProfile(farmerProfileEntity);

    userResponseDto = new UserResponse();
    userResponseDto.setId(1L);
    userResponseDto.setEmail("test@example.com");
    userResponseDto.setFullName("Test User");
    userResponseDto.setRoles(Set.of(RoleType.ROLE_CONSUMER.name()));

    userProfileResponseDto = new UserProfileResponse();
    // Kế thừa từ UserResponse nên các trường đó cũng cần được set
    userProfileResponseDto.setId(testUser.getId());
    userProfileResponseDto.setEmail(testUser.getEmail());
    userProfileResponseDto.setFullName(testUser.getFullName());
    userProfileResponseDto.setRoles(Set.of(RoleType.ROLE_CONSUMER.name()));
    // ... các trường khác của UserProfileResponse

    // Mock chung cho authentication
    lenient().when(authentication.getName()).thenReturn(testUser.getEmail());
    lenient().when(authentication.isAuthenticated()).thenReturn(true);
    lenient()
        .when(userRepository.findByEmail(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));

    // Mock cho JwtProperties (cần cho refreshToken)
    JwtProperties.RefreshToken mockRefreshTokenProps = new JwtProperties.RefreshToken();
    mockRefreshTokenProps.setExpirationMs(TimeUnit.DAYS.toMillis(7)); // 7 ngày
    lenient().when(jwtProperties.getRefreshToken()).thenReturn(mockRefreshTokenProps);
  }

  private void mockAuthenticatedUser(User user, RoleType roleType) {
    lenient().when(authentication.getPrincipal()).thenReturn(user);
    lenient().when(authentication.getName()).thenReturn(user.getEmail());
    lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    Collection<GrantedAuthority> authorities = new HashSet<>();
    authorities.add(new SimpleGrantedAuthority(roleType.name()));
    lenient().when(authentication.getAuthorities()).thenReturn((Collection) authorities);
  }

  @Nested
  @DisplayName("User Registration and Verification")
  class RegistrationAndVerification {
    @Test
    @DisplayName("Register User - Success")
    void registerUser_success() {
      when(userRepository.existsByEmailIgnoringSoftDelete(
              registrationRequest.getEmail().toLowerCase()))
          .thenReturn(false);
      when(userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber()))
          .thenReturn(false);
      when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
      when(passwordEncoder.encode(registrationRequest.getPassword())).thenReturn("encodedPassword");
      when(userRepository.saveAndFlush(any(User.class)))
          .thenAnswer(
              invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L); // Simulate ID generation
                u.setCreatedAt(LocalDateTime.now());
                u.setUpdatedAt(LocalDateTime.now());
                return u;
              });
      when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto);
      doNothing()
          .when(emailService)
          .sendVerificationEmail(any(User.class), anyString(), anyString());

      UserResponse result = userService.registerUser(registrationRequest);

      assertNotNull(result);
      assertEquals(userResponseDto.getEmail(), result.getEmail());
      verify(emailService)
          .sendVerificationEmail(
              any(User.class), anyString(), contains("/auth/verify-email?token="));
    }

    // ... (Thêm các test case lỗi cho registerUser như email/phone tồn tại, role không tìm thấy)
    // ...

    @Test
    @DisplayName("Verify Email - Success")
    void verifyEmail_success() {
      String token = "valid-token";
      testUser.setVerificationToken(token);
      testUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));
      testUser.setActive(false); // User chưa active

      when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(testUser));
      when(userRepository.save(testUser)).thenReturn(testUser);
      doNothing().when(notificationService).sendWelcomeNotification(testUser);

      boolean result = userService.verifyEmail(token);

      assertTrue(result);
      assertTrue(testUser.isActive());
      assertNull(testUser.getVerificationToken());
      verify(notificationService).sendWelcomeNotification(testUser);
    }

    @Test
    @DisplayName("Verify Email - Invalid Token")
    void verifyEmail_invalidToken_throwsBadRequest() {
      when(userRepository.findByVerificationToken("invalid-token")).thenReturn(Optional.empty());
      assertThrows(BadRequestException.class, () -> userService.verifyEmail("invalid-token"));
    }

    @Test
    @DisplayName("Verify Email - Expired Token")
    void verifyEmail_expiredToken_throwsBadRequest() {
      String token = "expired-token";
      testUser.setVerificationToken(token);
      testUser.setVerificationTokenExpiry(LocalDateTime.now().minusHours(1)); // Token đã hết hạn
      when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(testUser));

      assertThrows(BadRequestException.class, () -> userService.verifyEmail(token));
      verify(userRepository).save(testUser); // Token cũ được xóa
    }
  }

  @Nested
  @DisplayName("User Profile and Password Management")
  class ProfileAndPassword {
    @BeforeEach
    void authSetup() {
      mockAuthenticatedUser(testUser, RoleType.ROLE_CONSUMER);
    }

    @Test
    @DisplayName("Get Current User Profile - Success")
    void getCurrentUserProfile_success() {
      when(userMapper.toUserProfileResponse(testUser)).thenReturn(userProfileResponseDto);
      // Giả sử testUser không phải Farmer hay Business Buyer
      //
      // when(farmerProfileRepository.findById(testUser.getId())).thenReturn(Optional.empty());
      //
      // when(businessProfileRepository.findById(testUser.getId())).thenReturn(Optional.empty());

      UserProfileResponse result = userService.getCurrentUserProfile(authentication);

      assertNotNull(result);
      assertEquals(userProfileResponseDto.getEmail(), result.getEmail());
      assertNull(result.getFarmerProfile());
      assertNull(result.getBusinessProfile());
    }

    @Test
    @DisplayName("Change Password - Success")
    void changePassword_success() {
      PasswordChangeRequest request = new PasswordChangeRequest();
      request.setCurrentPassword("oldPassword");
      request.setNewPassword("newStrongPassword");

      when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
      when(passwordEncoder.encode("newStrongPassword")).thenReturn("newEncodedPassword");
      when(userRepository.save(testUser)).thenReturn(testUser);

      assertDoesNotThrow(() -> userService.changePassword(authentication, request));

      assertEquals("newEncodedPassword", testUser.getPasswordHash());
      assertNull(testUser.getRefreshToken()); // Refresh token bị vô hiệu hóa
      verify(userRepository, times(2)).save(testUser);
    }

    // ... (Thêm test case lỗi cho changePassword) ...

    @Test
    @DisplayName("Update Current User Profile - Success")
    void updateCurrentUserProfile_success() {
      UserUpdateRequest updateRequest = new UserUpdateRequest();
      updateRequest.setFullName("Updated Test User");
      updateRequest.setPhoneNumber("0900000000");

      when(userRepository.existsByPhoneNumber("0900000000")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(userMapper.toUserResponse(any(User.class)))
          .thenAnswer(
              inv -> {
                User u = inv.getArgument(0);
                UserResponse res = new UserResponse();
                res.setId(u.getId());
                res.setFullName(u.getFullName());
                res.setPhoneNumber(u.getPhoneNumber());
                return res;
              });

      UserResponse result = userService.updateCurrentUserProfile(authentication, updateRequest);

      assertNotNull(result);
      assertEquals("Updated Test User", result.getFullName());
      assertEquals("0900000000", testUser.getPhoneNumber());
    }
    // ... (Thêm test case lỗi cho updateCurrentUserProfile) ...
  }

  @Nested
  @DisplayName("Admin User Management")
  class AdminUserManagement {
    @BeforeEach
    void adminAuthSetup() {
      // Không cần mock authentication ở đây vì các phương thức admin không dùng nó trực tiếp
      // mà nhận ID hoặc các tham số khác.
    }

    @Test
    @DisplayName("Get All Users - Success")
    void getAllUsers_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);
      when(userRepository.findAll(pageable)).thenReturn(userPage);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);

      Page<UserResponse> result = userService.getAllUsers(pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(userResponseDto.getEmail(), result.getContent().get(0).getEmail());
    }

    @Test
    @DisplayName("Get User Profile By Id - Success")
    void getUserProfileById_success() {
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(userMapper.toUserProfileResponse(testUser)).thenReturn(userProfileResponseDto);
      // Giả sử user này không có farmer/business profile
      //
      // when(farmerProfileRepository.findById(testUser.getId())).thenReturn(Optional.empty());
      //
      // when(businessProfileRepository.findById(testUser.getId())).thenReturn(Optional.empty());

      UserProfileResponse result = userService.getUserProfileById(testUser.getId());
      assertNotNull(result);
      assertEquals(userProfileResponseDto.getEmail(), result.getEmail());
    }

    @Test
    @DisplayName("Update User Status - Success")
    void updateUserStatus_success() {
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(userRepository.save(testUser)).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);
      doNothing().when(notificationService).sendAccountStatusUpdateNotification(testUser, false);

      UserResponse result = userService.updateUserStatus(testUser.getId(), false); // Deactivate

      assertNotNull(result);
      assertFalse(testUser.isActive());
      verify(notificationService).sendAccountStatusUpdateNotification(testUser, false);
    }

    @Test
    @DisplayName("Update User Roles - Success")
    void updateUserRoles_success() {
      Set<RoleType> newRoleTypes = Set.of(RoleType.ROLE_FARMER, RoleType.ROLE_CONSUMER);
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(roleRepository.findByName(RoleType.ROLE_FARMER)).thenReturn(Optional.of(farmerRole));
      when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
      when(userRepository.save(testUser)).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser))
          .thenReturn(userResponseDto); // userResponseDto cần được cập nhật roles
      doNothing().when(notificationService).sendRolesUpdateNotification(testUser);

      UserResponse result = userService.updateUserRoles(testUser.getId(), newRoleTypes);

      assertNotNull(result);
      assertEquals(2, testUser.getRoles().size());
      assertTrue(testUser.getRoles().contains(farmerRole));
      verify(notificationService).sendRolesUpdateNotification(testUser);
    }
  }

  @Nested
  @DisplayName("Password Reset Flow")
  class PasswordResetFlow {
    @Test
    @DisplayName("Initiate Password Reset - User Exists")
    void initiatePasswordReset_userExists_sendsEmail() {
      String email = "user.exists@example.com";
      User existingUser = User.builder().id(5L).email(email).fullName("Existing User").build();
      when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenReturn(existingUser);
      doNothing()
          .when(emailService)
          .sendPasswordResetEmail(any(User.class), anyString(), anyString());

      userService.initiatePasswordReset(email);

      assertNotNull(existingUser.getVerificationToken());
      assertNotNull(existingUser.getVerificationTokenExpiry());
      verify(emailService)
          .sendPasswordResetEmail(
              eq(existingUser),
              eq(existingUser.getVerificationToken()),
              contains("/auth/reset-password?token="));
    }

    @Test
    @DisplayName("Initiate Password Reset - User Not Exists - Does Nothing Visible")
    void initiatePasswordReset_userNotExists_doesNothingVisible() {
      String email = "non.existent@example.com";
      when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

      userService.initiatePasswordReset(email);

      verify(userRepository, never()).save(any(User.class));
      verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Reset Password - Valid Token - Success")
    void resetPassword_validToken_success() {
      String token = "valid-reset-token";
      String newPassword = "newPassword123";
      testUser.setVerificationToken(token);
      testUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));

      when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
      when(userRepository.save(testUser)).thenReturn(testUser);
      doNothing().when(notificationService).sendPasswordChangedNotification(testUser);

      userService.resetPassword(token, newPassword);

      assertEquals("encodedNewPassword", testUser.getPasswordHash());
      assertNull(testUser.getVerificationToken());
      verify(notificationService).sendPasswordChangedNotification(testUser);
    }
    // ... (Thêm test case lỗi cho resetPassword: invalid token, expired token) ...
  }

  @Nested
  @DisplayName("Featured Farmers and Public Search")
  class PublicFarmerFeatures {
    @Test
    @DisplayName("Get Featured Farmers - Success")
    void getFeaturedFarmers_success() {
      Pageable pageable = PageRequest.of(0, 4);
      List<User> topFarmerUsers = List.of(farmerUser); // Giả sử farmerUser là top
      when(userRepository.findTopByRoles_NameOrderByFollowerCountDesc(
              RoleType.ROLE_FARMER, pageable))
          .thenReturn(topFarmerUsers);
      when(farmerProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.of(farmerProfileEntity));

      FarmerSummaryResponse summaryDto =
          new FarmerSummaryResponse(
              farmerUser.getId(),
              farmerProfileEntity1.getId(),
              farmerProfileEntity.getFarmName(),
              farmerUser.getFullName(),
              farmerUser.getAvatarUrl(),
              farmerProfileEntity.getProvinceCode(),
              farmerUser.getFollowerCount());
      when(farmerSummaryMapper.toFarmerSummaryResponse(farmerUser, farmerProfileEntity))
          .thenReturn(summaryDto);

      List<FarmerSummaryResponse> result = userService.getFeaturedFarmers(4);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(farmerProfileEntity.getFarmName(), result.get(0).getFarmName());
    }

    @Test
    @DisplayName("Search Public Farmers - Success")
    void searchPublicFarmers_success() {
      Pageable pageable = PageRequest.of(0, 10);
      String keyword = "Green";
      String provinceCode = "20";
      List<FarmerProfile> profiles = List.of(farmerProfileEntity);
      Page<FarmerProfile> profilePage = new PageImpl<>(profiles, pageable, profiles.size());

      when(farmerProfileRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(profilePage);
      FarmerSummaryResponse summaryDto =
          new FarmerSummaryResponse(
              farmerUser.getId(),
              farmerProfileEntity1.getId(),
              farmerProfileEntity.getFarmName(),
              farmerUser.getFullName(),
              farmerUser.getAvatarUrl(),
              farmerProfileEntity.getProvinceCode(),
              farmerUser.getFollowerCount());
      when(farmerSummaryMapper.toFarmerSummaryResponse(farmerUser, farmerProfileEntity))
          .thenReturn(summaryDto);

      Page<FarmerSummaryResponse> result =
          userService.searchPublicFarmers(keyword, provinceCode, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(summaryDto.getFarmName(), result.getContent().get(0).getFarmName());
    }
  }

  @Nested
  @DisplayName("OAuth2 and Token Management")
  class OAuthAndToken {
    // Mock GoogleIdTokenVerifier và các thành phần liên quan
    // Cần PowerMockito hoặc một cách khác để mock static method `GoogleIdTokenVerifier.Builder`
    // Hoặc inject trực tiếp GoogleIdTokenVerifier (đã được mock) vào service nếu có thể.
    // Trong ví dụ này, chúng ta sẽ giả định bạn có thể inject mock Verifier.
    // Nếu không, bạn cần cấu trúc lại code service để dễ test hơn hoặc dùng PowerMock.

    // Test cho processGoogleLogin sẽ phức tạp hơn do phụ thuộc vào thư viện Google.
    // Cần mock GoogleIdTokenVerifier, GoogleIdToken, GoogleIdToken.Payload.

    @Test
    @DisplayName("Refresh Token - Valid Token - Success")
    void refreshToken_validToken_success() {
      String oldRefreshToken = "valid-refresh-token";
      String newAccessToken = "new-access-token";
      String newRefreshToken = "new-refresh-token";
      testUser.setRefreshToken(oldRefreshToken);
      testUser.setRefreshTokenExpiryDate(LocalDateTime.now().plusDays(1));

      when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
      when(jwtTokenProvider.getEmailFromToken(oldRefreshToken)).thenReturn(testUser.getEmail());
      // userRepository.findByEmail đã được mock ở setUp hoặc mockAuthenticatedUser

      // Mock cho việc tạo token mới
      Collection<? extends GrantedAuthority> authorities =
          Set.of(new SimpleGrantedAuthority(RoleType.ROLE_CONSUMER.name()));
      Authentication authForTokenGeneration =
          new UsernamePasswordAuthenticationToken(testUser.getEmail(), null, authorities);

      when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
          .thenReturn(newAccessToken);
      when(jwtTokenProvider.generateRefreshToken(any(Authentication.class)))
          .thenReturn(newRefreshToken);

      when(userRepository.save(any(User.class))).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser))
          .thenReturn(userResponseDto); // userResponseDto cần có roles

      LoginResponse result = userService.refreshToken(oldRefreshToken);

      assertNotNull(result);
      assertEquals(newAccessToken, result.getAccessToken());
      assertEquals(newRefreshToken, result.getRefreshToken());
      assertEquals(newRefreshToken, testUser.getRefreshToken()); // Kiểm tra token mới đã được lưu
      assertNotNull(testUser.getRefreshTokenExpiryDate());
    }

    @Test
    @DisplayName("Refresh Token - Invalid Token (Validation Fails)")
    void refreshToken_invalidTokenValidation_throwsBadRequest() {
      String invalidToken = "invalid-refresh-token";
      when(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false);
      assertThrows(BadRequestException.class, () -> userService.refreshToken(invalidToken));
    }

    @Test
    @DisplayName("Refresh Token - Token Not Matching DB or Expired in DB")
    void refreshToken_tokenMismatchOrExpiredInDb_throwsBadRequest() {
      String clientToken = "client-refresh-token";
      testUser.setRefreshToken("db-refresh-token"); // Token trong DB khác
      testUser.setRefreshTokenExpiryDate(LocalDateTime.now().plusDays(1));

      when(jwtTokenProvider.validateToken(clientToken)).thenReturn(true);
      when(jwtTokenProvider.getEmailFromToken(clientToken)).thenReturn(testUser.getEmail());

      assertThrows(BadRequestException.class, () -> userService.refreshToken(clientToken));
      assertNull(testUser.getRefreshToken()); // Token trong DB bị vô hiệu hóa
    }

    @Test
    @DisplayName("Invalidate Refresh Token - Success")
    void invalidateRefreshTokenForUser_success() {
      testUser.setRefreshToken("some-token");
      testUser.setRefreshTokenExpiryDate(LocalDateTime.now().plusDays(1));
      when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
      when(userRepository.save(testUser)).thenReturn(testUser);

      userService.invalidateRefreshTokenForUser(testUser.getEmail());

      assertNull(testUser.getRefreshToken());
      assertNull(testUser.getRefreshTokenExpiryDate());
      verify(userRepository).save(testUser);
    }
  }
}
