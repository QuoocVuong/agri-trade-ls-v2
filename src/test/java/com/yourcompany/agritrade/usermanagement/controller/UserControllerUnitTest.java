package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.service.AdminUserService;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq; // Thêm eq
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    @Mock private UserService userService;
    @Mock private AdminUserService adminUserService;
    @Mock private Authentication authentication; // Vẫn giữ mock Authentication ở đây

    @InjectMocks
    private UserController userController;

    private UserProfileResponse userProfileResponse;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userProfileResponse = new UserProfileResponse();
        userProfileResponse.setId(1L);
        userProfileResponse.setEmail("user@example.com");
        userProfileResponse.setFullName("Test User Profile");
        // ... khởi tạo các trường khác cho userProfileResponse

        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setEmail("user@example.com");
        userResponse.setFullName("Test User Response");
        // ... khởi tạo các trường khác cho userResponse

        // *** BỎ STUBBING CHUNG NÀY KHỎI setUp() ***
        // when(authentication.getName()).thenReturn("user@example.com");
    }

    @Test
    void getCurrentUserProfile_success_returnsOkWithProfile() {
        // Arrange
        // Mock authentication.getName() CHỈ cho test này
        //when(authentication.getName()).thenReturn("user@example.com");
        when(userService.getCurrentUserProfile(authentication)).thenReturn(userProfileResponse);

        // Act
        ResponseEntity<ApiResponse<UserProfileResponse>> responseEntity = userController.getCurrentUserProfile(authentication);

        // Assert
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userProfileResponse, responseEntity.getBody().getData());
        verify(userService).getCurrentUserProfile(authentication);
    }

    @Test
    void changePassword_success_returnsOk() {
        // Arrange
        // Mock authentication.getName() CHỈ cho test này (nếu service cần)
        //when(authentication.getName()).thenReturn("user@example.com");
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("oldPass");
        request.setNewPassword("newPass");
        doNothing().when(userService).changePassword(authentication, request);

        // Act
        ResponseEntity<ApiResponse<Void>> responseEntity = userController.changePassword(authentication, request);

        // Assert
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Password changed successfully", responseEntity.getBody().getMessage());
        verify(userService).changePassword(authentication, request);
    }

    @Test
    void updateMyProfile_success_returnsOkWithUpdatedUser() {
        // Arrange
        // Mock authentication.getName() CHỈ cho test này
        //when(authentication.getName()).thenReturn("user@example.com");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullName("New Full Name");
        when(userService.updateCurrentUserProfile(authentication, request)).thenReturn(userResponse);

        // Act
        ResponseEntity<ApiResponse<UserResponse>> responseEntity = userController.updateMyProfile(authentication, request);

        // Assert
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userResponse, responseEntity.getBody().getData());
        assertEquals("Profile updated successfully", responseEntity.getBody().getMessage());
        verify(userService).updateCurrentUserProfile(authentication, request);
    }

    @Test
    void getAllUsersForAdmin_success_returnsPageOfUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserResponse> userPage = new PageImpl<>(List.of(userResponse), pageable, 1);
        // Phương thức này không dùng authentication, nên không cần mock authentication.getName()
        when(adminUserService.getAllUsers(eq(pageable), isNull(), isNull(), isNull())).thenReturn(userPage);

        // Act
        ResponseEntity<ApiResponse<Page<UserResponse>>> responseEntity =
                userController.getAllUsersForAdmin(null, null, null, pageable);

        // Assert
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userPage, responseEntity.getBody().getData());
        verify(adminUserService).getAllUsers(eq(pageable), isNull(), isNull(), isNull());
    }

    // TODO: Thêm test cho các endpoint admin khác của UserController:
    // - getUserProfileById (Admin)
    // - updateUserStatus (Admin)
    // - updateUserRoles (Admin)
    // và các kịch bản lỗi cho tất cả các phương thức.
}