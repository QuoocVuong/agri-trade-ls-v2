package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceImplUnitTest {

  @Mock private BusinessProfileRepository businessProfileRepository;
  @Mock private UserRepository userRepository;
  @Mock private BusinessProfileMapper businessProfileMapper;
  @Mock private RoleRepository roleRepository;
  @Mock private Authentication authentication;

  // SỬA LỖI: Thêm MockedStatic để quản lý mock cho SecurityUtils
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private BusinessProfileServiceImpl businessProfileService;

  private User currentUser;
  private BusinessProfileRequest profileRequest;
  private BusinessProfile existingProfile;
  private Role businessBuyerRole;

  @BeforeEach
  void setUp() {
    // SỬA LỖI: Khởi tạo mock static
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    currentUser = new User();
    currentUser.setId(1L);
    currentUser.setEmail("business@example.com");
    currentUser.setFullName("Business User");
    currentUser.setRoles(new HashSet<>());

    profileRequest = new BusinessProfileRequest();
    profileRequest.setBusinessName("Test Business Inc.");
    profileRequest.setTaxCode("1234567890");
    profileRequest.setBusinessProvinceCode("20");

    existingProfile = new BusinessProfile();
    existingProfile.setUserId(currentUser.getId());
    existingProfile.setUser(currentUser);
    existingProfile.setBusinessName("Old Business Name");
    existingProfile.setTaxCode("OLD_TAX_CODE");
    existingProfile.setBusinessProvinceCode("20");

    businessBuyerRole = new Role(RoleType.ROLE_BUSINESS_BUYER);
    businessBuyerRole.setId(2);
  }

  // SỬA LỖI: Thêm tearDown để đóng mock static sau mỗi test
  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  @Test
  void createOrUpdateBusinessProfile_createNewProfile_success() {
    // --- Arrange ---
    // SỬA LỖI: Mock hành vi cho SecurityUtils.getCurrentAuthenticatedUser()
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(currentUser);

    BusinessProfile profileToBeCreatedByMapper = new BusinessProfile();
    profileToBeCreatedByMapper.setBusinessName(profileRequest.getBusinessName());
    profileToBeCreatedByMapper.setTaxCode(profileRequest.getTaxCode());
    profileToBeCreatedByMapper.setBusinessProvinceCode(profileRequest.getBusinessProvinceCode());

    BusinessProfile savedNewProfile = new BusinessProfile();
    savedNewProfile.setUserId(currentUser.getId());
    savedNewProfile.setUser(currentUser);
    savedNewProfile.setBusinessName(profileRequest.getBusinessName());
    savedNewProfile.setTaxCode(profileRequest.getTaxCode());
    savedNewProfile.setBusinessProvinceCode(profileRequest.getBusinessProvinceCode());

    BusinessProfileResponse expectedResponse = new BusinessProfileResponse();
    expectedResponse.setUserId(currentUser.getId());
    expectedResponse.setBusinessName(profileRequest.getBusinessName());
    expectedResponse.setTaxCode(profileRequest.getTaxCode());
    expectedResponse.setBusinessProvinceCode(profileRequest.getBusinessProvinceCode());

    when(businessProfileRepository.existsById(currentUser.getId())).thenReturn(false);
    when(businessProfileRepository.findById(currentUser.getId())).thenReturn(Optional.empty());
    when(businessProfileMapper.requestToBusinessProfile(profileRequest))
        .thenReturn(profileToBeCreatedByMapper);
    when(businessProfileRepository.save(any(BusinessProfile.class))).thenReturn(savedNewProfile);
    when(roleRepository.findByName(RoleType.ROLE_BUSINESS_BUYER))
        .thenReturn(Optional.of(businessBuyerRole));
    when(userRepository.save(any(User.class))).thenReturn(currentUser);
    when(businessProfileMapper.toBusinessProfileResponse(savedNewProfile))
        .thenReturn(expectedResponse);

    // --- Act ---
    BusinessProfileResponse result =
        businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest);

    // --- Assert ---
    assertNotNull(result);
    assertEquals(expectedResponse.getBusinessName(), result.getBusinessName());
    assertEquals(expectedResponse.getTaxCode(), result.getTaxCode());

    verify(businessProfileRepository).existsById(currentUser.getId());
    verify(businessProfileMapper).requestToBusinessProfile(profileRequest);

    ArgumentCaptor<BusinessProfile> profileCaptor = ArgumentCaptor.forClass(BusinessProfile.class);
    verify(businessProfileRepository).save(profileCaptor.capture());
    BusinessProfile capturedProfile = profileCaptor.getValue();
    assertEquals(currentUser, capturedProfile.getUser());
    assertEquals(profileRequest.getBusinessName(), capturedProfile.getBusinessName());

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertTrue(userCaptor.getValue().getRoles().contains(businessBuyerRole));

    verify(businessProfileMapper).toBusinessProfileResponse(savedNewProfile);
  }

  @Test
  void createOrUpdateBusinessProfile_updateExistingProfile_success() {
    // --- Arrange ---
    // SỬA LỖI: Mock hành vi cho SecurityUtils.getCurrentAuthenticatedUser()
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(currentUser);

    BusinessProfileResponse expectedResponseAfterUpdate = new BusinessProfileResponse();
    expectedResponseAfterUpdate.setUserId(currentUser.getId());
    expectedResponseAfterUpdate.setBusinessName(profileRequest.getBusinessName());
    expectedResponseAfterUpdate.setTaxCode(profileRequest.getTaxCode());

    when(businessProfileRepository.existsById(currentUser.getId())).thenReturn(true);
    when(businessProfileRepository.findById(currentUser.getId()))
        .thenReturn(Optional.of(existingProfile));

    doAnswer(
            invocation -> {
              BusinessProfileRequest req = invocation.getArgument(0);
              BusinessProfile profileToUpdate = invocation.getArgument(1);
              profileToUpdate.setBusinessName(req.getBusinessName());
              profileToUpdate.setTaxCode(req.getTaxCode());
              profileToUpdate.setBusinessProvinceCode(req.getBusinessProvinceCode());
              return null;
            })
        .when(businessProfileMapper)
        .updateBusinessProfileFromRequest(eq(profileRequest), eq(existingProfile));

    when(businessProfileRepository.save(existingProfile)).thenReturn(existingProfile);
    when(businessProfileMapper.toBusinessProfileResponse(existingProfile))
        .thenReturn(expectedResponseAfterUpdate);

    // --- Act ---
    BusinessProfileResponse result =
        businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest);

    // --- Assert ---
    assertNotNull(result);
    assertEquals(profileRequest.getBusinessName(), result.getBusinessName());
    assertEquals(profileRequest.getTaxCode(), result.getTaxCode());

    assertEquals(profileRequest.getBusinessName(), existingProfile.getBusinessName());
    assertEquals(profileRequest.getTaxCode(), existingProfile.getTaxCode());

    verify(businessProfileRepository).findById(currentUser.getId());
    verify(businessProfileMapper).updateBusinessProfileFromRequest(profileRequest, existingProfile);
    verify(businessProfileRepository).save(existingProfile);
    verify(userRepository, never()).save(any(User.class));
    verify(businessProfileMapper).toBusinessProfileResponse(existingProfile);
  }

  @Test
  void getBusinessProfile_found_success() {
    // Arrange
    Long userIdToFind = 1L;
    BusinessProfileResponse expectedResponse = new BusinessProfileResponse();
    expectedResponse.setUserId(existingProfile.getUserId());
    expectedResponse.setBusinessName(existingProfile.getBusinessName());
    expectedResponse.setTaxCode(existingProfile.getTaxCode());

    when(businessProfileRepository.findById(userIdToFind)).thenReturn(Optional.of(existingProfile));
    when(businessProfileMapper.toBusinessProfileResponse(existingProfile))
        .thenReturn(expectedResponse);

    // Act
    BusinessProfileResponse result = businessProfileService.getBusinessProfile(userIdToFind);

    // Assert
    assertNotNull(result);
    assertEquals(existingProfile.getBusinessName(), result.getBusinessName());
    assertEquals(existingProfile.getUserId(), result.getUserId());

    verify(businessProfileRepository).findById(userIdToFind);
    verify(businessProfileMapper).toBusinessProfileResponse(existingProfile);
  }

  @Test
  void getBusinessProfile_notFound_throwsResourceNotFoundException() {
    // Arrange
    Long nonExistentUserId = 99L;
    when(businessProfileRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

    // Act & Assert
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> businessProfileService.getBusinessProfile(nonExistentUserId));
    assertEquals(
        String.format("BusinessProfile not found with userId : '%s'", nonExistentUserId),
        exception.getMessage());

    verify(businessProfileRepository).findById(nonExistentUserId);
    verify(businessProfileMapper, never()).toBusinessProfileResponse(any());
  }

  @Test
  void createOrUpdateBusinessProfile_userNotFound_throwsUsernameNotFoundException() {
    // Arrange
    // SỬA LỖI: Mock SecurityUtils để nó ném lỗi UsernameNotFoundException
    mockedSecurityUtils
        .when(SecurityUtils::getCurrentAuthenticatedUser)
        .thenThrow(new UsernameNotFoundException("User not found in DB"));

    // Act & Assert
    assertThrows(
        UsernameNotFoundException.class,
        () -> businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest));

    // Verify rằng logic đã dừng lại sau khi không tìm thấy user
    verify(businessProfileRepository, never()).existsById(any());
    verify(businessProfileRepository, never()).save(any());
    verify(userRepository, never()).save(any());
  }
}
