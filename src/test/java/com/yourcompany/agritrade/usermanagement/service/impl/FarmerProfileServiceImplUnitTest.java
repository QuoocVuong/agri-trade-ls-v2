package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.FarmerProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerProfileResponse;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class FarmerProfileServiceImplUnitTest {

  @Mock private FarmerProfileRepository farmerProfileRepository;
  @Mock private UserRepository userRepository;
  @Mock private FarmerProfileMapper farmerProfileMapper;
  @Mock private RoleRepository roleRepository;
  @Mock private Authentication authentication;

  // SỬA LỖI: Thêm biến để quản lý mock static
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private FarmerProfileServiceImpl farmerProfileService;

  private User currentUser;
  private FarmerProfileRequest profileRequest;
  private FarmerProfile existingProfile;
  private FarmerProfileResponse profileResponseDto;
  private Role farmerRole;

  @BeforeEach
  void setUp() {
    // SỬA LỖI: Khởi tạo mock static
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    currentUser = new User();
    currentUser.setId(1L);
    currentUser.setEmail("farmer@example.com");
    currentUser.setFullName("Farmer Test User");
    currentUser.setRoles(new HashSet<>());

    profileRequest = new FarmerProfileRequest();
    profileRequest.setFarmName("Green Acres Farm");
    profileRequest.setProvinceCode("20");

    existingProfile = new FarmerProfile();
    existingProfile.setUserId(currentUser.getId());
    existingProfile.setUser(currentUser);
    existingProfile.setFarmName("Old Farm Name");
    existingProfile.setVerificationStatus(VerificationStatus.VERIFIED);

    profileResponseDto = new FarmerProfileResponse();
    profileResponseDto.setUserId(currentUser.getId());

    farmerRole = new Role(RoleType.ROLE_FARMER);
    farmerRole.setId(3);
  }

  // SỬA LỖI: Thêm tearDown để đóng mock static
  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  @Test
  void createOrUpdateFarmerProfile_createNewProfile_success() {
    // --- Arrange ---
    // SỬA LỖI: Mock hành vi cho SecurityUtils.getCurrentAuthenticatedUser()
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(currentUser);

    FarmerProfile profileToBeCreatedByMapper = new FarmerProfile();
    profileToBeCreatedByMapper.setFarmName(profileRequest.getFarmName());
    profileToBeCreatedByMapper.setProvinceCode(profileRequest.getProvinceCode());

    FarmerProfile savedNewProfile = new FarmerProfile();
    savedNewProfile.setUserId(currentUser.getId());
    savedNewProfile.setUser(currentUser);
    savedNewProfile.setFarmName(profileRequest.getFarmName());
    savedNewProfile.setProvinceCode(profileRequest.getProvinceCode());
    savedNewProfile.setVerificationStatus(VerificationStatus.PENDING);

    FarmerProfileResponse expectedResponse = new FarmerProfileResponse();
    expectedResponse.setUserId(currentUser.getId());
    expectedResponse.setFarmName(profileRequest.getFarmName());
    expectedResponse.setVerificationStatus(VerificationStatus.PENDING);

    when(farmerProfileRepository.existsById(currentUser.getId())).thenReturn(false);
    when(farmerProfileRepository.findById(currentUser.getId()))
        .thenReturn(Optional.empty()); // Cần mock cho orElseGet
    when(farmerProfileMapper.requestToFarmerProfile(profileRequest))
        .thenReturn(profileToBeCreatedByMapper);
    when(farmerProfileRepository.save(any(FarmerProfile.class))).thenReturn(savedNewProfile);
    when(roleRepository.findByName(RoleType.ROLE_FARMER)).thenReturn(Optional.of(farmerRole));
    when(userRepository.save(any(User.class))).thenReturn(currentUser);
    when(farmerProfileMapper.toFarmerProfileResponse(savedNewProfile)).thenReturn(expectedResponse);

    // --- Act ---
    FarmerProfileResponse result =
        farmerProfileService.createOrUpdateFarmerProfile(authentication, profileRequest);

    // --- Assert ---
    assertNotNull(result);
    assertEquals(expectedResponse.getFarmName(), result.getFarmName());
    assertEquals(expectedResponse.getVerificationStatus(), result.getVerificationStatus());

    verify(farmerProfileRepository).existsById(currentUser.getId());
    verify(farmerProfileMapper).requestToFarmerProfile(profileRequest);

    ArgumentCaptor<FarmerProfile> profileCaptor = ArgumentCaptor.forClass(FarmerProfile.class);
    verify(farmerProfileRepository).save(profileCaptor.capture());
    FarmerProfile capturedProfile = profileCaptor.getValue();
    assertEquals(currentUser, capturedProfile.getUser());
    assertEquals(VerificationStatus.PENDING, capturedProfile.getVerificationStatus());

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertTrue(userCaptor.getValue().getRoles().contains(farmerRole));

    verify(farmerProfileMapper).toFarmerProfileResponse(savedNewProfile);
  }

  @Test
  void createOrUpdateFarmerProfile_updateExistingProfile_keepsVerificationStatus() {
    // --- Arrange ---
    // SỬA LỖI: Mock hành vi cho SecurityUtils.getCurrentAuthenticatedUser()
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(currentUser);

    FarmerProfileResponse expectedResponse = new FarmerProfileResponse();
    expectedResponse.setUserId(currentUser.getId());
    expectedResponse.setFarmName(profileRequest.getFarmName());
    expectedResponse.setVerificationStatus(VerificationStatus.VERIFIED);

    when(farmerProfileRepository.existsById(currentUser.getId())).thenReturn(true);
    when(farmerProfileRepository.findById(currentUser.getId()))
        .thenReturn(Optional.of(existingProfile));

    doAnswer(
            invocation -> {
              FarmerProfileRequest req = invocation.getArgument(0);
              FarmerProfile profileToUpdate = invocation.getArgument(1);
              profileToUpdate.setFarmName(req.getFarmName());
              return null;
            })
        .when(farmerProfileMapper)
        .updateFarmerProfileFromRequest(eq(profileRequest), eq(existingProfile));

    when(farmerProfileRepository.save(existingProfile)).thenReturn(existingProfile);
    when(farmerProfileMapper.toFarmerProfileResponse(existingProfile)).thenReturn(expectedResponse);

    // --- Act ---
    FarmerProfileResponse result =
        farmerProfileService.createOrUpdateFarmerProfile(authentication, profileRequest);

    // --- Assert ---
    assertNotNull(result);
    assertEquals(profileRequest.getFarmName(), result.getFarmName());
    assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());

    assertEquals(profileRequest.getFarmName(), existingProfile.getFarmName());
    assertEquals(VerificationStatus.VERIFIED, existingProfile.getVerificationStatus());

    verify(farmerProfileRepository).findById(currentUser.getId());
    verify(farmerProfileMapper).updateFarmerProfileFromRequest(profileRequest, existingProfile);
    verify(farmerProfileRepository).save(existingProfile);
    verify(userRepository, never()).save(any(User.class));
    verify(farmerProfileMapper).toFarmerProfileResponse(existingProfile);
  }

  @Test
  void getFarmerProfile_serviceImpl_found_success() {
    // Arrange
    Long farmerUserId = 1L;
    profileResponseDto.setFarmName(existingProfile.getFarmName());
    profileResponseDto.setVerificationStatus(existingProfile.getVerificationStatus());

    when(farmerProfileRepository.findById(farmerUserId)).thenReturn(Optional.of(existingProfile));
    when(farmerProfileMapper.toFarmerProfileResponse(existingProfile))
        .thenReturn(profileResponseDto);

    // Act
    FarmerProfileResponse result = farmerProfileService.getFarmerProfile(farmerUserId);

    // Assert
    assertNotNull(result);
    assertEquals(existingProfile.getFarmName(), result.getFarmName());
    assertEquals(existingProfile.getUserId(), result.getUserId());
    assertEquals(existingProfile.getVerificationStatus(), result.getVerificationStatus());

    verify(farmerProfileRepository).findById(farmerUserId);
    verify(farmerProfileMapper).toFarmerProfileResponse(existingProfile);
  }

  @Test
  void getFarmerProfile_notFound_throwsResourceNotFoundException() {
    // Arrange
    Long nonExistentUserId = 99L;
    when(farmerProfileRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

    // Act & Assert
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> farmerProfileService.getFarmerProfile(nonExistentUserId));

    String expectedMessage =
        String.format("FarmerProfile not found with userId : '%s'", nonExistentUserId);
    assertEquals(expectedMessage, exception.getMessage());

    verify(farmerProfileRepository).findById(nonExistentUserId);
    verify(farmerProfileMapper, never()).toFarmerProfileResponse(any());
  }
}
