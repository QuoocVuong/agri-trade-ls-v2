package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.config.properties.JwtProperties;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
// ... các import khác nếu cần cho các phương thức bạn muốn test ...

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtProperties jwtProperties;
    @Mock private JwtProperties.RefreshToken refreshTokenPropertiesMock;


    // Các mock khác nếu UserServiceImpl có thêm dependency
    // @Mock private FarmerProfileRepository farmerProfileRepository;
    // @Mock private BusinessProfileRepository businessProfileRepository;
    // @Mock private FarmerProfileMapper farmerProfileMapper;
    // @Mock private BusinessProfileMapper businessProfileMapper;
    // @Mock private FarmerSummaryMapper farmerSummaryMapper;


    @InjectMocks
    private UserServiceImpl userService;

    private UserRegistrationRequest registrationRequest;
    private Role consumerRole;
    private User userEntity;
    private UserResponse userResponseDto;

    @BeforeEach
    void setUp() {
        registrationRequest = new UserRegistrationRequest();
        registrationRequest.setEmail("test@example.com");
        registrationRequest.setPassword("password123");
        registrationRequest.setFullName("Test User");
        registrationRequest.setPhoneNumber("0123456789");

        consumerRole = new Role(RoleType.ROLE_CONSUMER);
        consumerRole.setId(1);

        userEntity = new User();
        userEntity.setId(1L);
        userEntity.setEmail(registrationRequest.getEmail().toLowerCase());
        userEntity.setFullName(registrationRequest.getFullName());
        userEntity.setPhoneNumber(registrationRequest.getPhoneNumber());
        userEntity.setPasswordHash("encodedPassword123");
        userEntity.setActive(false);
        userEntity.setProvider("LOCAL");
        userEntity.setRoles(Collections.singleton(consumerRole)); // Sẽ sửa nếu cần mutable
        userEntity.setVerificationToken(UUID.randomUUID().toString());
        userEntity.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));


        userResponseDto = new UserResponse();
        userResponseDto.setId(1L);
        userResponseDto.setEmail(registrationRequest.getEmail().toLowerCase());
        userResponseDto.setFullName(registrationRequest.getFullName());
        userResponseDto.setPhoneNumber(registrationRequest.getPhoneNumber());
        userResponseDto.setRoles(Set.of(RoleType.ROLE_CONSUMER.name()));
        userResponseDto.setActive(false);

        // Set giá trị cho các field được inject bằng @Value
        ReflectionTestUtils.setField(userService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(userService, "googleClientId", "test-google-client-id");



    }

    @Test
    void registerUser_success() {
        // Arrange
        when(userRepository.existsByEmailIgnoringSoftDelete(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
        when(passwordEncoder.encode(registrationRequest.getPassword())).thenReturn("encodedPassword123");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(userEntity);
        when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto);
        doNothing().when(emailService).sendVerificationEmail(any(User.class), anyString(), anyString());

        // Act
        UserResponse actualResponse = userService.registerUser(registrationRequest);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(userResponseDto.getEmail(), actualResponse.getEmail());
        assertEquals(userResponseDto.getFullName(), actualResponse.getFullName());

        verify(userRepository).existsByEmailIgnoringSoftDelete(registrationRequest.getEmail().toLowerCase());
        verify(userRepository).existsByPhoneNumber(registrationRequest.getPhoneNumber());
        verify(roleRepository).findByName(RoleType.ROLE_CONSUMER);
        verify(passwordEncoder).encode(registrationRequest.getPassword());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("test@example.com", capturedUser.getEmail());
        assertEquals("encodedPassword123", capturedUser.getPasswordHash());
        assertEquals("LOCAL", capturedUser.getProvider());
        assertFalse(capturedUser.isActive());
        assertNotNull(capturedUser.getVerificationToken());

        verify(emailService).sendVerificationEmail(eq(userEntity), eq(capturedUser.getVerificationToken()), anyString());
        verify(userMapper).toUserResponse(userEntity);
    }

    @Test
    void registerUser_emailAlreadyTaken_throwsBadRequestException() {
        when(userRepository.existsByEmailIgnoringSoftDelete("test@example.com")).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            userService.registerUser(registrationRequest);
        });

        assertEquals("Error: Email is already taken!", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerUser_phoneNumberAlreadyTaken_throwsBadRequestException() {
        when(userRepository.existsByEmailIgnoringSoftDelete(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber("0123456789")).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            userService.registerUser(registrationRequest);
        });
        assertEquals("Error: Phone number is already taken!", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void processLoginAuthentication_success() {
        // Arrange
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("test@example.com");

        User userFromRepo = new User(); // User lấy từ repo
        userFromRepo.setEmail("test@example.com");
        userFromRepo.setRoles(Collections.singleton(consumerRole)); // Sử dụng HashSet nếu cần thay đổi
        // ... set các trường khác cho userFromRepo

        UserResponse mappedUserResponse = new UserResponse(); // UserResponse sau khi map
        mappedUserResponse.setEmail("test@example.com");

        Set<Role> roles = new HashSet<>(); // Đảm bảo roles là mutable
        roles.add(consumerRole);
        userEntity.setRoles(roles);


        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(userFromRepo));
        when(jwtTokenProvider.generateAccessToken(mockAuth)).thenReturn("newAccessToken");
        when(jwtTokenProvider.generateRefreshToken(mockAuth)).thenReturn("newRefreshToken");
        when(userMapper.toUserResponse(userFromRepo)).thenReturn(mappedUserResponse);
        when(userRepository.save(any(User.class))).thenReturn(userFromRepo); // Giả lập save cho refresh token

        // *** THÊM CÁC STUBBING CẦN THIẾT CHO TEST NÀY VÀO ĐÂY ***
        when(jwtProperties.getRefreshToken()).thenReturn(refreshTokenPropertiesMock);
        when(refreshTokenPropertiesMock.getExpirationMs()).thenReturn(TimeUnit.DAYS.toMillis(7));
        // ******************************************************

        // Act
        LoginResponse loginResponse = userService.processLoginAuthentication(mockAuth);

        // Assert
        assertNotNull(loginResponse);
        assertEquals("newAccessToken", loginResponse.getAccessToken());
        assertEquals("newRefreshToken", loginResponse.getRefreshToken());
        assertEquals(mappedUserResponse, loginResponse.getUser());

        verify(userRepository).findByEmail("test@example.com");
        verify(jwtTokenProvider).generateAccessToken(mockAuth);
        verify(jwtTokenProvider).generateRefreshToken(mockAuth);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("newRefreshToken", userCaptor.getValue().getRefreshToken());
        assertNotNull(userCaptor.getValue().getRefreshTokenExpiryDate());



        verify(userMapper).toUserResponse(userFromRepo);
    }


    // TODO: Viết thêm Unit Test cho các phương thức quan trọng khác của UserServiceImpl:
    // - verifyEmail (thành công, token không hợp lệ, token hết hạn)
    // - refreshToken (thành công, token không hợp lệ/hết hạn, user không tồn tại, token DB không khớp)
    // - processGoogleLogin (các kịch bản: token hợp lệ/không hợp lệ, user mới, user đã tồn tại)
    // - changePassword (thành công, mật khẩu cũ sai)
    // - updateCurrentUserProfile (thành công, SĐT trùng)
    // - initiatePasswordReset
    // - resetPassword
    // - getFeaturedFarmers (mock userRepository.findTopByRoles_NameOrderByFollowerCountDesc và farmerProfileRepository.findById)
    // - searchPublicFarmers (mock farmerProfileRepository.findAll(Specification, Pageable))
}