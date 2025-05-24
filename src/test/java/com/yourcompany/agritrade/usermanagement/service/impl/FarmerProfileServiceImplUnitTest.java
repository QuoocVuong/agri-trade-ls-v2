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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Thêm nếu service có thể ném lỗi này

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmerProfileServiceImplUnitTest {

    @Mock private FarmerProfileRepository farmerProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private FarmerProfileMapper farmerProfileMapper;
    @Mock private RoleRepository roleRepository;
    @Mock private Authentication authentication; // Mock cho các phương thức cần Authentication

    @InjectMocks
    private FarmerProfileServiceImpl farmerProfileService;

    private User currentUser;
    private FarmerProfileRequest profileRequest;
    private FarmerProfile existingProfile;
    // newProfileEntity sẽ được tạo trong các test cụ thể nếu cần, không cần là biến instance
    private FarmerProfileResponse profileResponseDto; // DTO này sẽ được điều chỉnh trong từng test
    private Role farmerRole;

    @BeforeEach
    void setUp() {
        // Khởi tạo các đối tượng chung
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("farmer@example.com");
        currentUser.setFullName("Farmer Test User");
        currentUser.setRoles(new HashSet<>()); // Quan trọng: Khởi tạo bằng mutable set

        profileRequest = new FarmerProfileRequest();
        profileRequest.setFarmName("Green Acres Farm");
        profileRequest.setProvinceCode("20");
        // ... set các trường khác cho profileRequest nếu cần cho tất cả test

        existingProfile = new FarmerProfile();
        existingProfile.setUserId(currentUser.getId());
        existingProfile.setUser(currentUser);
        existingProfile.setFarmName("Old Farm Name");
        existingProfile.setVerificationStatus(VerificationStatus.VERIFIED);

        profileResponseDto = new FarmerProfileResponse(); // Sẽ được tùy chỉnh trong từng test
        profileResponseDto.setUserId(currentUser.getId());


        farmerRole = new Role(RoleType.ROLE_FARMER);
        farmerRole.setId(3); // Giả lập ID cho role
    }

    @Test
    void createOrUpdateFarmerProfile_createNewProfile_success() {
        // --- Arrange ---
        // Mock hành vi của authentication và userRepository cho test này
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        // Dữ liệu cho việc tạo mới
        FarmerProfile profileToBeCreatedByMapper = new FarmerProfile();
        // Giả sử mapper tạo ra đối tượng này từ request, chưa có user và userId
        profileToBeCreatedByMapper.setFarmName(profileRequest.getFarmName());
        profileToBeCreatedByMapper.setProvinceCode(profileRequest.getProvinceCode());
        // ... các trường khác từ profileRequest ...

        FarmerProfile savedNewProfile = new FarmerProfile(); // Entity sau khi service xử lý và save
        savedNewProfile.setUserId(currentUser.getId());
        savedNewProfile.setUser(currentUser);
        savedNewProfile.setFarmName(profileRequest.getFarmName());
        savedNewProfile.setProvinceCode(profileRequest.getProvinceCode());
        savedNewProfile.setVerificationStatus(VerificationStatus.PENDING); // Service sẽ set PENDING khi tạo mới

        // Thiết lập DTO response mong đợi cho test này
        FarmerProfileResponse expectedResponse = new FarmerProfileResponse();
        expectedResponse.setUserId(currentUser.getId());
        expectedResponse.setFarmName(profileRequest.getFarmName());
        expectedResponse.setVerificationStatus(VerificationStatus.PENDING);
        // ... các trường khác ...


        when(farmerProfileRepository.existsById(currentUser.getId())).thenReturn(false); // Profile chưa tồn tại
        when(farmerProfileMapper.requestToFarmerProfile(profileRequest)).thenReturn(profileToBeCreatedByMapper);
        when(farmerProfileRepository.save(any(FarmerProfile.class))).thenReturn(savedNewProfile);
        when(roleRepository.findByName(RoleType.ROLE_FARMER)).thenReturn(Optional.of(farmerRole));
        when(userRepository.save(any(User.class))).thenReturn(currentUser); // User sau khi thêm role
        when(farmerProfileMapper.toFarmerProfileResponse(savedNewProfile)).thenReturn(expectedResponse);

        // --- Act ---
        FarmerProfileResponse result = farmerProfileService.createOrUpdateFarmerProfile(authentication, profileRequest);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(expectedResponse.getFarmName(), result.getFarmName());
        assertEquals(expectedResponse.getVerificationStatus(), result.getVerificationStatus());

        verify(farmerProfileRepository).existsById(currentUser.getId());
        verify(farmerProfileMapper).requestToFarmerProfile(profileRequest);

        ArgumentCaptor<FarmerProfile> profileCaptor = ArgumentCaptor.forClass(FarmerProfile.class);
        verify(farmerProfileRepository).save(profileCaptor.capture());
        FarmerProfile capturedProfile = profileCaptor.getValue();
        assertEquals(currentUser, capturedProfile.getUser()); // Kiểm tra user đã được gán vào profile trước khi save
        assertEquals(VerificationStatus.PENDING, capturedProfile.getVerificationStatus());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertTrue(userCaptor.getValue().getRoles().contains(farmerRole)); // Kiểm tra role FARMER đã được thêm

        verify(farmerProfileMapper).toFarmerProfileResponse(savedNewProfile);
    }

    @Test
    void createOrUpdateFarmerProfile_updateExistingProfile_keepsVerificationStatus() {
        // --- Arrange ---
        // Mock hành vi của authentication và userRepository cho test này
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        // existingProfile đã được tạo trong setUp với status VERIFIED và farmName "Old Farm Name"
        // profileRequest có farmName "Green Acres Farm"

        // Thiết lập DTO response mong đợi cho test này
        FarmerProfileResponse expectedResponse = new FarmerProfileResponse();
        expectedResponse.setUserId(currentUser.getId());
        expectedResponse.setFarmName(profileRequest.getFarmName()); // Tên mới sau khi update
        expectedResponse.setVerificationStatus(VerificationStatus.VERIFIED); // Status được giữ nguyên
        // ... các trường khác ...

        when(farmerProfileRepository.existsById(currentUser.getId())).thenReturn(true); // Profile đã tồn tại
        when(farmerProfileRepository.findById(currentUser.getId())).thenReturn(Optional.of(existingProfile));

        // Giả lập hành vi của mapper.updateFarmerProfileFromRequest
        // Nó là void method, nên ta dùng doAnswer để mô phỏng việc nó thay đổi existingProfile
        doAnswer(invocation -> {
            FarmerProfileRequest req = invocation.getArgument(0);
            FarmerProfile profileToUpdate = invocation.getArgument(1);
            profileToUpdate.setFarmName(req.getFarmName()); // Giả lập mapper cập nhật tên
            // ... mapper sẽ cập nhật các trường khác từ req vào profileToUpdate ...
            return null;
        }).when(farmerProfileMapper).updateFarmerProfileFromRequest(eq(profileRequest), eq(existingProfile));

        // Khi save, existingProfile (đã được mapper sửa đổi) sẽ được truyền vào
        when(farmerProfileRepository.save(existingProfile)).thenReturn(existingProfile);
        when(farmerProfileMapper.toFarmerProfileResponse(existingProfile)).thenReturn(expectedResponse);

        // --- Act ---
        FarmerProfileResponse result = farmerProfileService.createOrUpdateFarmerProfile(authentication, profileRequest);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(profileRequest.getFarmName(), result.getFarmName()); // Tên đã được cập nhật
        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus()); // Status không đổi

        // Kiểm tra xem existingProfile (đối tượng mock) có được cập nhật tên không
        assertEquals(profileRequest.getFarmName(), existingProfile.getFarmName());
        assertEquals(VerificationStatus.VERIFIED, existingProfile.getVerificationStatus());


        verify(farmerProfileRepository).findById(currentUser.getId());
        verify(farmerProfileMapper).updateFarmerProfileFromRequest(profileRequest, existingProfile);
        verify(farmerProfileRepository).save(existingProfile);
        verify(userRepository, never()).save(any(User.class)); // Không thêm role lại
        verify(farmerProfileMapper).toFarmerProfileResponse(existingProfile);
    }

    @Test
    void getFarmerProfile_serviceImpl_found_success() {
        // Arrange
        Long farmerUserId = 1L;
        // existingProfile đã được tạo trong setUp với farmName = "Old Farm Name"
        // và userId = currentUser.getId() (là 1L)

        // Thiết lập DTO response mong đợi cho test này
        profileResponseDto.setFarmName(existingProfile.getFarmName());
        profileResponseDto.setVerificationStatus(existingProfile.getVerificationStatus());
        // ... các trường khác của profileResponseDto từ existingProfile ...

        when(farmerProfileRepository.findById(farmerUserId)).thenReturn(Optional.of(existingProfile));
        when(farmerProfileMapper.toFarmerProfileResponse(existingProfile)).thenReturn(profileResponseDto);

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
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            farmerProfileService.getFarmerProfile(nonExistentUserId);
        });

        // *** SỬA LẠI CHUỖI MONG ĐỢI Ở ĐÂY ***
        String expectedMessage = String.format("FarmerProfile not found with userId : '%s'", nonExistentUserId);
        assertEquals(expectedMessage, exception.getMessage());
        // ************************************

        verify(farmerProfileRepository).findById(nonExistentUserId);
        verify(farmerProfileMapper, never()).toFarmerProfileResponse(any());
    }

    // TODO: Thêm các test case khác:
    // - createOrUpdateFarmerProfile khi user không tìm thấy (ném UsernameNotFoundException)
    // - createOrUpdateFarmerProfile khi role FARMER không tìm thấy (ném ResourceNotFoundException)
}