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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    @Mock private UserService userService;
    @Mock private AdminUserService adminUserService; // Nếu UserController gọi AdminUserService
    @Mock private Authentication authentication;

    @InjectMocks
    private UserController userController;

    private UserProfileResponse userProfileResponse;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userProfileResponse = new UserProfileResponse(); /* ... khởi tạo ... */
        userResponse = new UserResponse(); /* ... khởi tạo ... */

        when(authentication.getName()).thenReturn("user@example.com"); // Giả lập tên user
    }

    @Test
    void getCurrentUserProfile_success_returnsOkWithProfile() {
        when(userService.getCurrentUserProfile(authentication)).thenReturn(userProfileResponse);

        ResponseEntity<ApiResponse<UserProfileResponse>> responseEntity = userController.getCurrentUserProfile(authentication);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userProfileResponse, responseEntity.getBody().getData());
        verify(userService).getCurrentUserProfile(authentication);
    }

    @Test
    void changePassword_success_returnsOk() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("oldPass");
        request.setNewPassword("newPass");
        doNothing().when(userService).changePassword(authentication, request);

        ResponseEntity<ApiResponse<Void>> responseEntity = userController.changePassword(authentication, request);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Password changed successfully", responseEntity.getBody().getMessage());
        verify(userService).changePassword(authentication, request);
    }

    @Test
    void updateMyProfile_success_returnsOkWithUpdatedUser() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullName("New Full Name");
        when(userService.updateCurrentUserProfile(authentication, request)).thenReturn(userResponse);

        ResponseEntity<ApiResponse<UserResponse>> responseEntity = userController.updateMyProfile(authentication, request);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userResponse, responseEntity.getBody().getData());
        assertEquals("Profile updated successfully", responseEntity.getBody().getMessage());
        verify(userService).updateCurrentUserProfile(authentication, request);
    }

    // --- Admin Endpoints (Ví dụ) ---
    @Test
    void getAllUsersForAdmin_success_returnsPageOfUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserResponse> userPage = new PageImpl<>(List.of(userResponse), pageable, 1);
        when(adminUserService.getAllUsers(eq(pageable), any(), any(), any())).thenReturn(userPage);

        ResponseEntity<ApiResponse<Page<UserResponse>>> responseEntity =
                userController.getAllUsersForAdmin(null, null, null, pageable);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userPage, responseEntity.getBody().getData());
        verify(adminUserService).getAllUsers(eq(pageable), any(), any(), any());
    }

    // TODO: Thêm test cho các endpoint khác của UserController (getUserProfileById, updateUserStatus, updateUserRoles)
    // và các kịch bản lỗi.
}