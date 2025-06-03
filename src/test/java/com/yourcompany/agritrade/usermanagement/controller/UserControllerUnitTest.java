package com.yourcompany.agritrade.usermanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.service.AdminUserService;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
// Không đặt @WithMockUser ở cấp lớp vì có cả API public (nếu có) và API cần quyền khác nhau
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AdminUserService adminUserService;

    // Authentication sẽ được mock hoặc cung cấp bởi @WithMockUser trong từng test case

    private UserProfileResponse userProfileResponse;
    private UserResponse userResponse;
    private Page<UserResponse> userResponsePage;
    private PasswordChangeRequest passwordChangeRequest;
    private UserUpdateRequest userUpdateRequest;

    @BeforeEach
    void setUp() {
        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setEmail("test@example.com");
        userResponse.setFullName("Test User");
        userResponse.setRoles(Set.of("ROLE_CONSUMER"));

        userProfileResponse = new UserProfileResponse();
        // Kế thừa từ UserResponse, nên gán các giá trị tương tự
        userProfileResponse.setId(1L);
        userProfileResponse.setEmail("test@example.com");
        userProfileResponse.setFullName("Test User Profile");
        userProfileResponse.setRoles(Set.of("ROLE_CONSUMER"));
        // userProfileResponse.setFarmerProfile(...); // Khởi tạo nếu cần

        userResponsePage = new PageImpl<>(List.of(userResponse));

        passwordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setCurrentPassword("oldPassword");
        passwordChangeRequest.setNewPassword("newStrongPassword");

        userUpdateRequest = new UserUpdateRequest();
        userUpdateRequest.setFullName("Updated Test User");
    }

    @Nested
    @DisplayName("Kiểm tra API Người dùng (Đã xác thực)")
    @WithMockUser // Giả lập người dùng đã đăng nhập cho các API /me/**
    class AuthenticatedUserApis {

        @Test
        @DisplayName("GET /api/users/me/profile - Lấy Profile Hiện tại - Thành công")
        void getCurrentUserProfile_success() throws Exception {
            when(userService.getCurrentUserProfile(any(Authentication.class))).thenReturn(userProfileResponse);

            mockMvc.perform(get("/api/users/me/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.email", is(userProfileResponse.getEmail())));
        }

        @Test
        @DisplayName("PUT /api/users/me/password - Đổi Mật khẩu - Thành công")
        void changePassword_success() throws Exception {
            doNothing().when(userService).changePassword(any(Authentication.class), any(PasswordChangeRequest.class));

            mockMvc.perform(put("/api/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(passwordChangeRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Password changed successfully")));
        }

        @Test
        @DisplayName("PUT /api/users/me/password - Mật khẩu hiện tại không đúng")
        void changePassword_incorrectCurrentPassword_throwsBadRequest() throws Exception {
            doThrow(new BadRequestException("Incorrect current password"))
                    .when(userService).changePassword(any(Authentication.class), any(PasswordChangeRequest.class));

            mockMvc.perform(put("/api/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(passwordChangeRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Incorrect current password")));
        }

        @Test
        @DisplayName("PUT /api/users/me - Cập nhật Profile Hiện tại - Thành công")
        void updateMyProfile_success() throws Exception {
            when(userService.updateCurrentUserProfile(any(Authentication.class), any(UserUpdateRequest.class)))
                    .thenReturn(userResponse);

            mockMvc.perform(put("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userUpdateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Profile updated successfully")))
                    .andExpect(jsonPath("$.data.fullName", is(userResponse.getFullName())));
        }
    }

    @Nested
    @DisplayName("Kiểm tra API Admin")
    @WithMockUser(roles = {"ADMIN"}) // Giả lập người dùng có vai trò ADMIN
    class AdminApis {

        @Test
        @DisplayName("GET /api/users - Lấy Tất cả Người dùng (Admin) - Thành công với bộ lọc")
        void getAllUsersForAdmin_withFilters_success() throws Exception {
            when(adminUserService.getAllUsers(any(Pageable.class), eq(RoleType.ROLE_FARMER), eq("keyword"), eq(true)))
                    .thenReturn(userResponsePage);

            mockMvc.perform(get("/api/users")
                            .param("role", "ROLE_FARMER")
                            .param("keyword", "keyword")
                            .param("isActive", "true")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].email", is(userResponse.getEmail())));
        }

        @Test
        @DisplayName("GET /api/users - Lấy Tất cả Người dùng (Admin) - Vai trò không hợp lệ")
        void getAllUsersForAdmin_invalidRole_callsServiceWithNullRole() throws Exception {
            when(adminUserService.getAllUsers(any(Pageable.class), isNull(), isNull(), isNull()))
                    .thenReturn(userResponsePage);

            mockMvc.perform(get("/api/users")
                            .param("role", "INVALID_ROLE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));

            verify(adminUserService).getAllUsers(any(Pageable.class), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("GET /api/users/{id}/profile - Lấy Profile theo ID (Admin) - Thành công")
        void getUserProfileById_success() throws Exception {
            Long userId = 1L;
            when(userService.getUserProfileById(eq(userId))).thenReturn(userProfileResponse);

            mockMvc.perform(get("/api/users/{id}/profile", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is(userId.intValue())));
        }

        @Test
        @DisplayName("GET /api/users/{id}/profile - Lấy Profile theo ID (Admin) - Không tìm thấy")
        void getUserProfileById_notFound() throws Exception {
            Long userId = 99L;
            when(userService.getUserProfileById(eq(userId)))
                    .thenThrow(new ResourceNotFoundException("User", "id", userId));

            mockMvc.perform(get("/api/users/{id}/profile", userId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("User not found with id : '99'")));
        }

        @Test
        @DisplayName("PUT /api/users/{id}/status - Cập nhật Trạng thái User (Admin) - Thành công")
        void updateUserStatus_success() throws Exception {
            Long userId = 1L;
            boolean isActive = false;
            userResponse.setActive(isActive); // Giả sử userResponse được cập nhật
            when(userService.updateUserStatus(eq(userId), eq(isActive))).thenReturn(userResponse);

            mockMvc.perform(put("/api/users/{id}/status", userId)
                            .param("isActive", String.valueOf(isActive)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("User status updated")))
                    .andExpect(jsonPath("$.data.active", is(isActive)));
        }

        @Test
        @DisplayName("PUT /api/users/{id}/roles - Cập nhật Vai trò User (Admin) - Thành công")
        void updateUserRoles_success() throws Exception {
            Long userId = 1L;
            Set<RoleType> newRoles = Set.of(RoleType.ROLE_FARMER);
            userResponse.setRoles(Set.of("ROLE_FARMER")); // Giả sử userResponse được cập nhật
            when(userService.updateUserRoles(eq(userId), eq(newRoles))).thenReturn(userResponse);

            mockMvc.perform(put("/api/users/{id}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRoles)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("User roles updated")))
                    .andExpect(jsonPath("$.data.roles[0]", is("ROLE_FARMER")));
        }
    }
}
