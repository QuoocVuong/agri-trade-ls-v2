package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.config.properties.JwtProperties; // Cần nếu test refreshToken
import com.yourcompany.agritrade.config.security.JwtTokenProvider; // Cần nếu test refreshToken
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;


import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService; // Thêm mock này
    @Mock
    private JwtTokenProvider jwtTokenProvider; // Thêm nếu test các hàm liên quan token
    @Mock
    private JwtProperties jwtProperties; // Thêm nếu test các hàm liên quan token

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
        consumerRole.setId(1); // Giả sử ID

        // User entity sẽ được tạo bởi service, nhưng ta cần nó để mock saveAndFlush
        userEntity = new User();
        userEntity.setId(1L);
        userEntity.setEmail(registrationRequest.getEmail().toLowerCase());
        userEntity.setFullName(registrationRequest.getFullName());
        userEntity.setPhoneNumber(registrationRequest.getPhoneNumber());
        userEntity.setPasswordHash("encodedPassword123"); // Giả sử đã encode
        userEntity.setActive(false); // Mặc định khi đăng ký
        userEntity.setProvider("LOCAL");
        Set<Role> roles = new HashSet<>();
        roles.add(consumerRole);
        userEntity.setRoles(roles);
        userEntity.setVerificationToken(UUID.randomUUID().toString());
        userEntity.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());


        userResponseDto = new UserResponse();
        userResponseDto.setId(1L);
        userResponseDto.setEmail(registrationRequest.getEmail().toLowerCase());
        userResponseDto.setFullName(registrationRequest.getFullName());
        userResponseDto.setPhoneNumber(registrationRequest.getPhoneNumber());
        Set<String> roleNames = new HashSet<>();
        roleNames.add(RoleType.ROLE_CONSUMER.name());
        userResponseDto.setRoles(roleNames);
        userResponseDto.setActive(false);
        userResponseDto.setCreatedAt(userEntity.getCreatedAt());


        // Set giá trị cho @Value field
        ReflectionTestUtils.setField(userService, "frontendUrl", "http://localhost:4200");
    }

    @Test
    @DisplayName("TC1.1: Register User Successfully")
    void registerUser_whenValidRequest_shouldCreateUserAndSendVerificationEmail() {
        // Arrange
        when(userRepository.existsByEmailIgnoringSoftDelete(registrationRequest.getEmail().toLowerCase())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.of(consumerRole));
        when(passwordEncoder.encode(registrationRequest.getPassword())).thenReturn("encodedPassword123");

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(1L);
            userToSave.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            userToSave.setCreatedAt(LocalDateTime.now());
            userToSave.setUpdatedAt(LocalDateTime.now());
            return userToSave;
        });

        when(userMapper.toUserResponse(any(User.class))).thenReturn(userResponseDto);
        doNothing().when(emailService).sendVerificationEmail(any(User.class), anyString(), anyString());

        // Act
        UserResponse actualResponse = userService.registerUser(registrationRequest);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(userResponseDto.getEmail(), actualResponse.getEmail());

        // Capture tham số truyền vào phương thức gửi email
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(userCaptor.capture(), tokenCaptor.capture(), urlCaptor.capture());

        String actualToken = userCaptor.getValue().getVerificationToken();
        assertNotNull(actualToken);
        assertEquals(actualToken, tokenCaptor.getValue());
        assertTrue(urlCaptor.getValue().contains(actualToken));
    }





    @Test
    @DisplayName("TC1.2: Register User Fails When Email Already Exists")
    void registerUser_whenEmailExists_shouldThrowBadRequestException() {
        // Arrange
        when(userRepository.existsByEmailIgnoringSoftDelete(registrationRequest.getEmail().toLowerCase())).thenReturn(true);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            userService.registerUser(registrationRequest);
        });
        assertEquals("Error: Email is already taken!", exception.getMessage());

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    @DisplayName("TC1.3: Register User Fails When Phone Number Already Exists")
    void registerUser_whenPhoneNumberExists_shouldThrowBadRequestException() {
        // Arrange
        when(userRepository.existsByEmailIgnoringSoftDelete(registrationRequest.getEmail().toLowerCase())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber())).thenReturn(true);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            userService.registerUser(registrationRequest);
        });
        assertEquals("Error: Phone number is already taken!", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("TC1.4: Register User Fails When Default Role Not Found")
    void registerUser_whenDefaultRoleNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(userRepository.existsByEmailIgnoringSoftDelete(registrationRequest.getEmail().toLowerCase())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_CONSUMER)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userService.registerUser(registrationRequest);
        });
        assertTrue(exception.getMessage().contains("Role not found with name : 'ROLE_CONSUMER'"));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    // TODO: Viết thêm Unit Test cho các phương thức khác của UserServiceImpl
    // Ví dụ: verifyEmail, initiatePasswordReset, resetPassword, processGoogleLogin, refreshToken,...
}