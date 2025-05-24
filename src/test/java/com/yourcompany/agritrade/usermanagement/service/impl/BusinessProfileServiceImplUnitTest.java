package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException; // Thêm nếu service của bạn có thể ném lỗi này
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceImplUnitTest {

    @Mock private BusinessProfileRepository businessProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private BusinessProfileMapper businessProfileMapper;
    @Mock private RoleRepository roleRepository;
    @Mock private Authentication authentication; // Mock cho các phương thức cần Authentication

    @InjectMocks
    private BusinessProfileServiceImpl businessProfileService;

    private User currentUser;
    private BusinessProfileRequest profileRequest;
    private BusinessProfile existingProfile;
    // newProfileEntity sẽ được tạo trong các test cụ thể nếu cần
    private Role businessBuyerRole;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("business@example.com");
        currentUser.setFullName("Business User");
        currentUser.setRoles(new HashSet<>()); // Khởi tạo roles rỗng ban đầu, sẽ được thêm trong test nếu cần

        profileRequest = new BusinessProfileRequest();
        profileRequest.setBusinessName("Test Business Inc.");
        profileRequest.setTaxCode("1234567890");
        profileRequest.setBusinessProvinceCode("20");
        // ... set các trường khác cho request

        existingProfile = new BusinessProfile();
        existingProfile.setUserId(currentUser.getId());
        existingProfile.setUser(currentUser);
        existingProfile.setBusinessName("Old Business Name");
        existingProfile.setTaxCode("OLD_TAX_CODE");
        existingProfile.setBusinessProvinceCode("20");
        // ...

        businessBuyerRole = new Role(RoleType.ROLE_BUSINESS_BUYER);
        businessBuyerRole.setId(2); // Giả lập ID
    }

    @Test
    void createOrUpdateBusinessProfile_createNewProfile_success() {
        // --- Arrange ---
        // Mock hành vi của authentication và userRepository cho test này
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        // Dữ liệu cho việc tạo mới
        BusinessProfile profileToBeCreatedByMapper = new BusinessProfile();
        profileToBeCreatedByMapper.setBusinessName(profileRequest.getBusinessName());
        profileToBeCreatedByMapper.setTaxCode(profileRequest.getTaxCode());
        profileToBeCreatedByMapper.setBusinessProvinceCode(profileRequest.getBusinessProvinceCode());
        // User sẽ được service gán

        BusinessProfile savedNewProfile = new BusinessProfile(); // Entity sau khi service xử lý và save
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


        when(businessProfileRepository.existsById(currentUser.getId())).thenReturn(false); // Profile chưa tồn tại
        when(businessProfileMapper.requestToBusinessProfile(profileRequest)).thenReturn(profileToBeCreatedByMapper);
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenReturn(savedNewProfile);
        when(roleRepository.findByName(RoleType.ROLE_BUSINESS_BUYER)).thenReturn(Optional.of(businessBuyerRole));
        when(userRepository.save(any(User.class))).thenReturn(currentUser); // User sau khi thêm role
        when(businessProfileMapper.toBusinessProfileResponse(savedNewProfile)).thenReturn(expectedResponse);

        // --- Act ---
        BusinessProfileResponse result = businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest);

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
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        // profileRequest có businessName = "Test Business Inc."
        // existingProfile có businessName = "Old Business Name"

        BusinessProfileResponse expectedResponseAfterUpdate = new BusinessProfileResponse();
        expectedResponseAfterUpdate.setUserId(currentUser.getId());
        expectedResponseAfterUpdate.setBusinessName(profileRequest.getBusinessName()); // Tên mới
        expectedResponseAfterUpdate.setTaxCode(profileRequest.getTaxCode()); // Tax code mới
        // ... các trường khác từ profileRequest ...


        when(businessProfileRepository.existsById(currentUser.getId())).thenReturn(true);
        when(businessProfileRepository.findById(currentUser.getId())).thenReturn(Optional.of(existingProfile));

        doAnswer(invocation -> {
            BusinessProfileRequest req = invocation.getArgument(0);
            BusinessProfile profileToUpdate = invocation.getArgument(1);
            // Giả lập mapper cập nhật các trường từ request vào existingProfile
            profileToUpdate.setBusinessName(req.getBusinessName());
            profileToUpdate.setTaxCode(req.getTaxCode());
            profileToUpdate.setBusinessProvinceCode(req.getBusinessProvinceCode());
            // ...
            return null;
        }).when(businessProfileMapper).updateBusinessProfileFromRequest(eq(profileRequest), eq(existingProfile));

        // Giả sử save trả về existingProfile đã được cập nhật
        when(businessProfileRepository.save(existingProfile)).thenReturn(existingProfile);
        // Mapper sẽ map existingProfile (đã được cập nhật) sang DTO
        when(businessProfileMapper.toBusinessProfileResponse(existingProfile)).thenReturn(expectedResponseAfterUpdate);

        // --- Act ---
        BusinessProfileResponse result = businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(profileRequest.getBusinessName(), result.getBusinessName()); // Kiểm tra tên đã được cập nhật trong DTO trả về
        assertEquals(profileRequest.getTaxCode(), result.getTaxCode());

        // Kiểm tra xem existingProfile (đối tượng mock) có được cập nhật không
        assertEquals(profileRequest.getBusinessName(), existingProfile.getBusinessName());
        assertEquals(profileRequest.getTaxCode(), existingProfile.getTaxCode());


        verify(businessProfileRepository).findById(currentUser.getId());
        verify(businessProfileMapper).updateBusinessProfileFromRequest(profileRequest, existingProfile);
        verify(businessProfileRepository).save(existingProfile);
        verify(userRepository, never()).save(any(User.class)); // Không cập nhật role cho user
        verify(businessProfileMapper).toBusinessProfileResponse(existingProfile);
    }

    @Test
    void getBusinessProfile_found_success() {
        // Arrange
        Long userIdToFind = 1L;
        // existingProfile được tạo trong setUp với businessName = "Old Business Name"

        // Tạo DTO response mong đợi khớp với existingProfile
        BusinessProfileResponse expectedResponse = new BusinessProfileResponse();
        expectedResponse.setUserId(existingProfile.getUserId());
        expectedResponse.setBusinessName(existingProfile.getBusinessName());
        expectedResponse.setTaxCode(existingProfile.getTaxCode());
        // ... map các trường khác từ existingProfile ...


        when(businessProfileRepository.findById(userIdToFind)).thenReturn(Optional.of(existingProfile));
        when(businessProfileMapper.toBusinessProfileResponse(existingProfile)).thenReturn(expectedResponse);

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
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            businessProfileService.getBusinessProfile(nonExistentUserId);
        });
        // Đảm bảo message khớp với cách ResourceNotFoundException của bạn tạo message
        assertEquals(String.format("BusinessProfile not found with userId : '%s'", nonExistentUserId), exception.getMessage());

        verify(businessProfileRepository).findById(nonExistentUserId);
        verify(businessProfileMapper, never()).toBusinessProfileResponse(any());
    }

    @Test
    void createOrUpdateBusinessProfile_userNotFound_throwsUsernameNotFoundException() {
        // Arrange
        // Mock hành vi của authentication
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(authentication.isAuthenticated()).thenReturn(true);
        // Quan trọng: Mock userRepository trả về empty để giả lập user không tồn tại
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UsernameNotFoundException.class, () -> {
            businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest);
        });
        verify(userRepository).findByEmail(currentUser.getEmail());
        verify(businessProfileRepository, never()).existsById(any());
    }
}