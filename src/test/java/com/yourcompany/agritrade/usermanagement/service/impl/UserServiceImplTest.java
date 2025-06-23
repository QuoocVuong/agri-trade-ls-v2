package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.common.util.SecurityUtils;
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
import com.yourcompany.agritrade.usermanagement.dto.response.*;
import com.yourcompany.agritrade.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerSummaryMapper;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
  @Mock private Authentication authentication;

  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private GoogleIdToken googleIdToken;
  @Mock private GoogleIdToken.Payload googleIdTokenPayload;

  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private UserServiceImpl userService;

  private UserRegistrationRequest registrationRequest;
  private User testUser, farmerUser, businessUser;
  private Role consumerRole, farmerRole, businessBuyerRole;
  private UserResponse userResponseDto;
  private UserProfileResponse userProfileResponseDto;
  private FarmerProfile farmerProfileEntity;

  @BeforeEach
  void setUp() {
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

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
    businessBuyerRole = new Role(RoleType.ROLE_BUSINESS_BUYER);
    businessBuyerRole.setId(3);

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

    businessUser =
        User.builder()
            .id(3L)
            .email("business@example.com")
            .fullName("Business User")
            .passwordHash("encodedPassword")
            .isActive(true)
            .roles(new HashSet<>(Set.of(businessBuyerRole)))
            .provider("LOCAL")
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
    userProfileResponseDto.setId(testUser.getId());
    userProfileResponseDto.setEmail(testUser.getEmail());
    userProfileResponseDto.setFullName(testUser.getFullName());
    userProfileResponseDto.setRoles(Set.of(RoleType.ROLE_CONSUMER.name()));

    JwtProperties.RefreshToken mockRefreshTokenProps = new JwtProperties.RefreshToken();
    mockRefreshTokenProps.setExpirationMs(TimeUnit.DAYS.toMillis(7));
    lenient().when(jwtProperties.getRefreshToken()).thenReturn(mockRefreshTokenProps);

    lenient()
        .when(userRepository.findByEmail(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
  }

  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  private void mockAuthenticatedUser(User user) {
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(user);
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
                u.setId(1L);
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
      verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("Register User - Email Already Exists - Throws BadRequestException")
    void registerUser_emailExists_throwsBadRequest() {
      when(userRepository.existsByEmailIgnoringSoftDelete(
              registrationRequest.getEmail().toLowerCase()))
          .thenReturn(true);
      assertThrows(BadRequestException.class, () -> userService.registerUser(registrationRequest));
      verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("Register User - Phone Number Already Exists - Throws BadRequestException")
    void registerUser_phoneNumberExists_throwsBadRequest() {
      when(userRepository.existsByEmailIgnoringSoftDelete(
              registrationRequest.getEmail().toLowerCase()))
          .thenReturn(false);
      when(userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber()))
          .thenReturn(true);
      assertThrows(BadRequestException.class, () -> userService.registerUser(registrationRequest));
      verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("Verify Email - Success")
    void verifyEmail_success() {
      String token = "valid-token";
      testUser.setVerificationToken(token);
      testUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));
      testUser.setActive(false);

      when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(testUser));
      when(userRepository.save(testUser)).thenReturn(testUser);
      doNothing().when(notificationService).sendWelcomeNotification(testUser);

      boolean result = userService.verifyEmail(token);

      assertTrue(result);
      assertTrue(testUser.isActive());
      assertNull(testUser.getVerificationToken());
      verify(notificationService).sendWelcomeNotification(testUser);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Verify Email - Invalid Token - Throws BadRequestException")
    void verifyEmail_invalidToken_throwsBadRequest() {
      when(userRepository.findByVerificationToken("invalid-token")).thenReturn(Optional.empty());
      assertThrows(BadRequestException.class, () -> userService.verifyEmail("invalid-token"));
    }

    @Test
    @DisplayName("Verify Email - Expired Token - Throws BadRequestException")
    void verifyEmail_expiredToken_throwsBadRequest() {
      String token = "expired-token";
      testUser.setVerificationToken(token);
      testUser.setVerificationTokenExpiry(LocalDateTime.now().minusHours(1));
      when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(testUser));

      assertThrows(BadRequestException.class, () -> userService.verifyEmail(token));
      verify(userRepository).save(testUser);
    }
  }

  @Nested
  @DisplayName("User Profile and Password Management")
  class ProfileAndPassword {

    @Test
    @DisplayName("Get Current User Profile - Success (Consumer)")
    void getCurrentUserProfile_consumer_success() {
      mockAuthenticatedUser(testUser);
      when(userMapper.toUserProfileResponse(testUser)).thenReturn(userProfileResponseDto);
      lenient()
          .when(farmerProfileRepository.findById(testUser.getId()))
          .thenReturn(Optional.empty());
      lenient()
          .when(businessProfileRepository.findById(testUser.getId()))
          .thenReturn(Optional.empty());

      UserProfileResponse result = userService.getCurrentUserProfile(authentication);

      assertNotNull(result);
      assertEquals(userProfileResponseDto.getEmail(), result.getEmail());
      assertNull(result.getFarmerProfile());
      assertNull(result.getBusinessProfile());
      verify(userMapper).toUserProfileResponse(testUser);
    }

    @Test
    @DisplayName("Get Current User Profile - Success (Farmer)")
    void getCurrentUserProfile_farmer_success() {
      mockAuthenticatedUser(farmerUser);
      UserProfileResponse farmerUserProfileResponse = new UserProfileResponse();
      farmerUserProfileResponse.setId(farmerUser.getId());
      farmerUserProfileResponse.setEmail(farmerUser.getEmail());
      farmerUserProfileResponse.setRoles(new HashSet<>(Set.of(RoleType.ROLE_FARMER.name())));

      when(userMapper.toUserProfileResponse(farmerUser)).thenReturn(farmerUserProfileResponse);
      when(farmerProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.of(farmerProfileEntity));
      when(farmerProfileMapper.toFarmerProfileResponse(farmerProfileEntity))
          .thenReturn(new FarmerProfileResponse());
      lenient()
          .when(businessProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.empty());

      UserProfileResponse result = userService.getCurrentUserProfile(authentication);

      assertNotNull(result);
      assertEquals(farmerUser.getEmail(), result.getEmail());
      assertNotNull(result.getFarmerProfile());
      verify(farmerProfileRepository).findById(farmerUser.getId());
    }

    @Test
    @DisplayName("Change Password - Success")
    void changePassword_success() {
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getName()).thenReturn(testUser.getEmail());

      PasswordChangeRequest request = new PasswordChangeRequest();
      request.setCurrentPassword("oldPassword");
      request.setNewPassword("newStrongPassword");
      testUser.setPasswordHash("oldEncodedPassword");

      when(passwordEncoder.matches("oldPassword", "oldEncodedPassword")).thenReturn(true);
      when(passwordEncoder.encode("newStrongPassword")).thenReturn("newEncodedPassword");
      when(userRepository.save(testUser)).thenReturn(testUser);

      userService.changePassword(authentication, request);

      assertEquals("newEncodedPassword", testUser.getPasswordHash());
      assertNull(testUser.getRefreshToken());
      verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Change Password - Incorrect Current Password - Throws BadRequestException")
    void changePassword_incorrectCurrentPassword_throwsBadRequest() {
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getName()).thenReturn(testUser.getEmail());

      PasswordChangeRequest request = new PasswordChangeRequest();
      request.setCurrentPassword("wrongPassword");
      request.setNewPassword("newStrongPassword");
      testUser.setPasswordHash("oldEncodedPassword");

      when(passwordEncoder.matches("wrongPassword", "oldEncodedPassword")).thenReturn(false);

      assertThrows(
          BadRequestException.class, () -> userService.changePassword(authentication, request));
      verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Update Current User Profile - Success")
    void updateCurrentUserProfile_success() {
      mockAuthenticatedUser(testUser);
      UserUpdateRequest updateRequest = new UserUpdateRequest();
      updateRequest.setFullName("Updated Test User");
      updateRequest.setPhoneNumber("0900000000");
      updateRequest.setAvatarUrl("new_avatar.jpg");

      testUser.setPhoneNumber("0123456789");

      when(userRepository.existsByPhoneNumber("0900000000")).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto);

      UserResponse result = userService.updateCurrentUserProfile(authentication, updateRequest);

      assertNotNull(result);
      assertEquals("Updated Test User", testUser.getFullName());
      assertEquals("0900000000", testUser.getPhoneNumber());
      assertEquals("new_avatar.jpg", testUser.getAvatarUrl());
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName(
        "Update Current User Profile - Phone Number Already Taken - Throws BadRequestException")
    void updateCurrentUserProfile_phoneNumberTaken_throwsBadRequest() {
      mockAuthenticatedUser(testUser);
      UserUpdateRequest updateRequest = new UserUpdateRequest();
      updateRequest.setPhoneNumber("0900000000");
      testUser.setPhoneNumber("0123456789");

      when(userRepository.existsByPhoneNumber("0900000000")).thenReturn(true);

      assertThrows(
          BadRequestException.class,
          () -> userService.updateCurrentUserProfile(authentication, updateRequest));
      verify(userRepository, never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("Admin User Management")
  class AdminUserManagement {
    @Test
    @DisplayName("Get All Users - Success")
    void getAllUsers_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<User> userPage = new PageImpl<>(List.of(farmerUser), pageable, 1);

      when(userRepository.findAll(pageable)).thenReturn(userPage);
      when(userMapper.toUserResponse(farmerUser)).thenReturn(userResponseDto);

      Page<UserResponse> result = userService.getAllUsers(pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      verify(userRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Get User Profile By Id - Success (Farmer User)")
    void getUserProfileById_farmerUser_success() {
      when(userRepository.findById(farmerUser.getId())).thenReturn(Optional.of(farmerUser));
      when(userMapper.toUserProfileResponse(farmerUser)).thenReturn(new UserProfileResponse());
      when(farmerProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.of(farmerProfileEntity));
      when(farmerProfileMapper.toFarmerProfileResponse(farmerProfileEntity))
          .thenReturn(new FarmerProfileResponse());

      UserProfileResponse result = userService.getUserProfileById(farmerUser.getId());

      assertNotNull(result);
      verify(farmerProfileRepository).findById(farmerUser.getId());
      verify(farmerProfileMapper).toFarmerProfileResponse(farmerProfileEntity);
    }

    @Test
    @DisplayName("Get User Profile By Id - User Not Found - Throws ResourceNotFoundException")
    void getUserProfileById_userNotFound_throwsResourceNotFound() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());
      assertThrows(ResourceNotFoundException.class, () -> userService.getUserProfileById(99L));
    }

    @Test
    @DisplayName("Update User Status - Success")
    void updateUserStatus_success() {
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(userRepository.save(testUser)).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);
      doNothing().when(notificationService).sendAccountStatusUpdateNotification(testUser, false);

      UserResponse result = userService.updateUserStatus(testUser.getId(), false);

      assertNotNull(result);
      assertFalse(testUser.isActive());
      verify(notificationService).sendAccountStatusUpdateNotification(testUser, false);
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Update User Roles - Success")
    void updateUserRoles_success() {
      Set<RoleType> newRoleTypes = Set.of(RoleType.ROLE_FARMER, RoleType.ROLE_CONSUMER);
      when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
      when(roleRepository.findByName(RoleType.ROLE_FARMER)).thenReturn(Optional.of(farmerRole));
      when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
      when(userRepository.save(testUser)).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);
      doNothing().when(notificationService).sendRolesUpdateNotification(testUser);

      UserResponse result = userService.updateUserRoles(testUser.getId(), newRoleTypes);

      assertNotNull(result);
      assertEquals(2, testUser.getRoles().size());
      assertTrue(testUser.getRoles().contains(farmerRole));
      verify(notificationService).sendRolesUpdateNotification(testUser);
      verify(userRepository).save(testUser);
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
      verify(userRepository).save(existingUser);
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
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Reset Password - Invalid Token - Throws BadRequestException")
    void resetPassword_invalidToken_throwsBadRequest() {
      when(userRepository.findByVerificationToken("invalid-token")).thenReturn(Optional.empty());
      assertThrows(
          BadRequestException.class, () -> userService.resetPassword("invalid-token", "newPass"));
    }

    @Test
    @DisplayName("Reset Password - Expired Token - Throws BadRequestException")
    void resetPassword_expiredToken_throwsBadRequest() {
      String token = "expired-token";
      testUser.setVerificationToken(token);
      testUser.setVerificationTokenExpiry(LocalDateTime.now().minusHours(1));
      when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(testUser));

      assertThrows(BadRequestException.class, () -> userService.resetPassword(token, "newPass"));
      verify(userRepository).save(testUser);
    }
  }

  @Nested
  @DisplayName("Featured Farmers and Public Search")
  class PublicFarmerFeatures {
    @Test
    @DisplayName("Get Featured Farmers - Success")
    void getFeaturedFarmers_success() {
      Pageable pageable = PageRequest.of(0, 4);
      List<User> topFarmerUsers = List.of(farmerUser);
      when(userRepository.findTopByRoles_NameOrderByFollowerCountDesc(
              RoleType.ROLE_FARMER, pageable))
          .thenReturn(topFarmerUsers);
      when(farmerProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.of(farmerProfileEntity));

      FarmerSummaryResponse summaryDto = new FarmerSummaryResponse();
      summaryDto.setUserId(farmerUser.getId());
      summaryDto.setFarmName(farmerProfileEntity.getFarmName());

      when(farmerSummaryMapper.toFarmerSummaryResponse(farmerUser, farmerProfileEntity))
          .thenReturn(summaryDto);

      List<FarmerSummaryResponse> result = userService.getFeaturedFarmers(4);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(farmerProfileEntity.getFarmName(), result.get(0).getFarmName());
      verify(userRepository)
          .findTopByRoles_NameOrderByFollowerCountDesc(RoleType.ROLE_FARMER, pageable);
    }

    @Test
    @DisplayName("Search Public Farmers - Success")
    void searchPublicFarmers_success() {
      Pageable pageable = PageRequest.of(0, 10);
      String keyword = "Green";
      String provinceCode = "20";
      Page<FarmerProfile> profilePage = new PageImpl<>(List.of(farmerProfileEntity), pageable, 1);

      when(farmerProfileRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(profilePage);
      FarmerSummaryResponse summaryDto = new FarmerSummaryResponse();
      summaryDto.setUserId(farmerUser.getId());
      summaryDto.setFarmName(farmerProfileEntity.getFarmName());
      when(farmerSummaryMapper.toFarmerSummaryResponse(farmerUser, farmerProfileEntity))
          .thenReturn(summaryDto);

      Page<FarmerSummaryResponse> result =
          userService.searchPublicFarmers(keyword, provinceCode, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(summaryDto.getFarmName(), result.getContent().get(0).getFarmName());
      verify(farmerProfileRepository).findAll(any(Specification.class), eq(pageable));
    }
  }

  @Nested
  @DisplayName("OAuth2 and Token Management")
  class OAuthAndToken {
    @Test
    @DisplayName("Process Login Authentication - Success")
    void processLoginAuthentication_success() {
      Authentication auth =
          new UsernamePasswordAuthenticationToken(
              testUser.getEmail(), "password", Collections.emptyList());
      String accessToken = "new-access-token";
      String refreshToken = "new-refresh-token";

      when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
      when(jwtTokenProvider.generateAccessToken(auth)).thenReturn(accessToken);
      when(jwtTokenProvider.generateRefreshToken(auth)).thenReturn(refreshToken);
      when(userRepository.save(any(User.class))).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);

      LoginResponse result = userService.processLoginAuthentication(auth);

      assertNotNull(result);
      assertEquals(accessToken, result.getAccessToken());
      assertEquals(refreshToken, result.getRefreshToken());
      assertEquals(refreshToken, testUser.getRefreshToken());
      assertNotNull(testUser.getRefreshTokenExpiryDate());
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Process Google Login - New User - Success")
    void processGoogleLogin_newUser_success() throws GeneralSecurityException, IOException {
      String googleIdTokenString = "google-id-token";
      String googleEmail = "new.google@example.com";
      String googleName = "New Google User";
      String googlePicture = "google.com/pic.jpg";
      String googleSubject = "google_sub_id";

      try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder =
          Mockito.mockConstruction(
              GoogleIdTokenVerifier.Builder.class,
              (mock, context) -> {
                when(mock.setAudience(anyList())).thenReturn(mock);
                when(mock.build()).thenReturn(googleIdTokenVerifier);
              })) {
        when(googleIdTokenVerifier.verify(googleIdTokenString)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(googleIdTokenPayload);
        when(googleIdTokenPayload.getSubject()).thenReturn(googleSubject);
        when(googleIdTokenPayload.getEmail()).thenReturn(googleEmail);
        when(googleIdTokenPayload.getEmailVerified()).thenReturn(true);
        when(googleIdTokenPayload.get("name")).thenReturn(googleName);
        when(googleIdTokenPayload.get("picture")).thenReturn(googlePicture);

        when(userRepository.findByEmail(googleEmail)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleType.ROLE_CONSUMER))
            .thenReturn(Optional.of(consumerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("randomEncodedPassword");
        when(userRepository.save(any(User.class)))
            .thenAnswer(
                inv -> {
                  User newUser = inv.getArgument(0);
                  newUser.setId(4L);
                  return newUser;
                });
        when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
            .thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(any(Authentication.class)))
            .thenReturn("new-refresh");
        when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());

        LoginResponse result = userService.processGoogleLogin(googleIdTokenString);

        assertNotNull(result);
        verify(userRepository, times(2)).save(any(User.class));
      }
    }

    @Test
    @DisplayName("Process Google Login - Existing Local User - Success (Link Account)")
    void processGoogleLogin_existingLocalUser_success()
        throws GeneralSecurityException, IOException {
      String googleIdTokenString = "google-id-token";
      String googleEmail = testUser.getEmail();
      String googleName = "Updated Google Name";
      String googlePicture = "google.com/updated_pic.jpg";
      String googleSubject = "google_sub_id";

      testUser.setProvider("LOCAL");
      testUser.setAvatarUrl("old_avatar.jpg");
      testUser.setFullName("Old Local Name");
      testUser.setActive(false);

      try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder =
          Mockito.mockConstruction(
              GoogleIdTokenVerifier.Builder.class,
              (mock, context) -> {
                when(mock.setAudience(anyList())).thenReturn(mock);
                when(mock.build()).thenReturn(googleIdTokenVerifier);
              })) {
        when(googleIdTokenVerifier.verify(googleIdTokenString)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(googleIdTokenPayload);
        when(googleIdTokenPayload.getSubject()).thenReturn(googleSubject);
        when(googleIdTokenPayload.getEmail()).thenReturn(googleEmail);
        when(googleIdTokenPayload.getEmailVerified()).thenReturn(true);
        when(googleIdTokenPayload.get("name")).thenReturn(googleName);
        when(googleIdTokenPayload.get("picture")).thenReturn(googlePicture);

        when(userRepository.findByEmail(googleEmail)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
            .thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(any(Authentication.class)))
            .thenReturn("new-refresh");
        when(userMapper.toUserResponse(any(User.class))).thenReturn(new UserResponse());

        userService.processGoogleLogin(googleIdTokenString);

        assertEquals("GOOGLE", testUser.getProvider());
        assertEquals(googleSubject, testUser.getProviderId());
        assertEquals(googleName, testUser.getFullName());
        assertEquals(googlePicture, testUser.getAvatarUrl());
        assertTrue(testUser.isActive());
        assertNull(testUser.getVerificationToken());
        verify(userRepository, times(2)).save(testUser);
      }
    }

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

      when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
          .thenReturn(newAccessToken);
      when(jwtTokenProvider.generateRefreshToken(any(Authentication.class)))
          .thenReturn(newRefreshToken);
      when(userRepository.save(any(User.class))).thenReturn(testUser);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);

      LoginResponse result = userService.refreshToken(oldRefreshToken);

      assertNotNull(result);
      assertEquals(newAccessToken, result.getAccessToken());
      assertEquals(newRefreshToken, result.getRefreshToken());
      assertEquals(newRefreshToken, testUser.getRefreshToken());
      assertNotNull(testUser.getRefreshTokenExpiryDate());
      verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Refresh Token - Invalid Token (Validation Fails)")
    void refreshToken_invalidTokenValidation_throwsBadRequest() {
      String invalidToken = "invalid-refresh-token";
      when(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false);
      assertThrows(BadRequestException.class, () -> userService.refreshToken(invalidToken));
    }

    @Test
    @DisplayName(
        "Refresh Token - Token Not Matching DB or Expired in DB - Throws BadRequestException")
    void refreshToken_tokenMismatchOrExpiredInDb_throwsBadRequest() {
      String clientToken = "client-refresh-token";
      testUser.setRefreshToken("db-refresh-token");
      testUser.setRefreshTokenExpiryDate(LocalDateTime.now().plusDays(1));

      when(jwtTokenProvider.validateToken(clientToken)).thenReturn(true);
      when(jwtTokenProvider.getEmailFromToken(clientToken)).thenReturn(testUser.getEmail());

      assertThrows(BadRequestException.class, () -> userService.refreshToken(clientToken));
      assertNull(testUser.getRefreshToken());
      verify(userRepository).save(testUser);
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

  @Nested
  @DisplayName("Search Buyers")
  class SearchBuyersTests {
    @Test
    @DisplayName("Search Buyers - Success with Keyword")
    void searchBuyers_withKeyword_success() {
      Pageable pageable = PageRequest.of(0, 10);
      String keyword = "test";
      Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);

      when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);
      when(userMapper.toUserResponse(testUser)).thenReturn(userResponseDto);

      Page<UserResponse> result = userService.searchBuyers(keyword, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(userResponseDto.getEmail(), result.getContent().get(0).getEmail());
      verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Search Buyers - No Keyword - Success")
    void searchBuyers_noKeyword_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<User> userPage =
          new PageImpl<>(List.of(testUser, farmerUser, businessUser), pageable, 3);

      when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);
      when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto);

      Page<UserResponse> result = userService.searchBuyers(null, pageable);

      assertNotNull(result);
      assertEquals(3, result.getTotalElements());
      verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }
  }
}
