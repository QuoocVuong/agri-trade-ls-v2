package com.yourcompany.agritrade.interaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.interaction.dto.response.FollowUserResponse;
import com.yourcompany.agritrade.interaction.service.FollowService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FollowController.class)
@Import(TestSecurityConfig.class)
// Không đặt @WithMockUser ở cấp lớp vì có API public và API cần quyền
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FollowService followService;

    // Authentication sẽ được cung cấp bởi @WithMockUser trong từng test case nếu cần

    private FollowUserResponse followUserResponse;
    private Page<FollowUserResponse> followUserResponsePage;

    @BeforeEach
    void setUp() {
        followUserResponse = new FollowUserResponse();
        followUserResponse.setUserId(2L);
        followUserResponse.setFullName("Followed User");
        followUserResponse.setAvatarUrl("avatar.jpg");

        followUserResponsePage = new PageImpl<>(List.of(followUserResponse));
    }

    @Nested
    @DisplayName("Kiểm tra API Theo dõi và Hủy theo dõi")
    @WithMockUser // Yêu cầu xác thực cho các API này
    class FollowUnfollowTests {
        @Test
        @DisplayName("POST /api/follows/following/{followingId} - Theo dõi User - Thành công")
        void followUser_success() throws Exception {
            Long followingId = 2L;
            doNothing().when(followService).followUser(any(Authentication.class), eq(followingId));

            mockMvc.perform(post("/api/follows/following/{followingId}", followingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("User followed successfully")));
        }

        @Test
        @DisplayName("POST /api/follows/following/{followingId} - User không tồn tại để theo dõi")
        void followUser_userNotFound_throwsNotFound() throws Exception {
            Long followingId = 99L;
            doThrow(new ResourceNotFoundException("User", "id", followingId))
                    .when(followService).followUser(any(Authentication.class), eq(followingId));

            mockMvc.perform(post("/api/follows/following/{followingId}", followingId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("User not found with id : '99'")));
        }

        @Test
        @DisplayName("POST /api/follows/following/{followingId} - Không thể tự theo dõi")
        void followUser_cannotFollowSelf_throwsBadRequest() throws Exception {
            Long selfId = 1L; // Giả sử ID của người dùng hiện tại là 1L (từ @WithMockUser)
            doThrow(new BadRequestException("You cannot follow yourself."))
                    .when(followService).followUser(any(Authentication.class), eq(selfId));

            mockMvc.perform(post("/api/follows/following/{followingId}", selfId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("You cannot follow yourself.")));
        }

        @Test
        @DisplayName("DELETE /api/follows/following/{followingId} - Hủy theo dõi User - Thành công")
        void unfollowUser_success() throws Exception {
            Long followingId = 2L;
            doNothing().when(followService).unfollowUser(any(Authentication.class), eq(followingId));

            mockMvc.perform(delete("/api/follows/following/{followingId}", followingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("User unfollowed successfully")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Lấy Danh sách Theo dõi")
    class GetFollowListsTests {
        @Test
        @WithMockUser
        @DisplayName("GET /api/follows/following/my - Lấy Danh sách Người Tôi Đang Theo dõi - Thành công")
        void getMyFollowing_success() throws Exception {
            when(followService.getFollowing(any(Authentication.class), any(Pageable.class)))
                    .thenReturn(followUserResponsePage);

            mockMvc.perform(get("/api/follows/following/my")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].userId", is(followUserResponse.getUserId().intValue())));
        }

        @Test
        @WithMockUser
        @DisplayName("GET /api/follows/followers/my - Lấy Danh sách Người Theo dõi Tôi - Thành công")
        void getMyFollowers_success() throws Exception {
            when(followService.getFollowers(any(Authentication.class), any(Pageable.class)))
                    .thenReturn(followUserResponsePage);

            mockMvc.perform(get("/api/follows/followers/my")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].userId", is(followUserResponse.getUserId().intValue())));
        }

        @Test
        @DisplayName("GET /api/follows/followers/user/{userId} - Lấy Danh sách Người Theo dõi của User Khác (Public) - Thành công")
        void getFollowersPublic_success() throws Exception {
            Long userId = 3L;
            when(followService.getFollowersPublic(eq(userId), any(Pageable.class)))
                    .thenReturn(followUserResponsePage);

            mockMvc.perform(get("/api/follows/followers/user/{userId}", userId)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].userId", is(followUserResponse.getUserId().intValue())));
        }

        @Test
        @DisplayName("GET /api/follows/followers/user/{userId} - User không tồn tại (Public)")
        void getFollowersPublic_userNotFound_throwsNotFound() throws Exception {
            Long userId = 99L;
            when(followService.getFollowersPublic(eq(userId), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("User", "id", userId));

            mockMvc.perform(get("/api/follows/followers/user/{userId}", userId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("User not found with id : '99'")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Trạng thái Theo dõi")
    @WithMockUser
    class CheckFollowingStatusTests {
        @Test
        @DisplayName("GET /api/follows/following/status/{followingId} - Đang theo dõi - Trả về true")
        void checkFollowingStatus_isFollowing_returnsTrue() throws Exception {
            Long followingId = 2L;
            when(followService.isFollowing(any(Authentication.class), eq(followingId))).thenReturn(true);

            mockMvc.perform(get("/api/follows/following/status/{followingId}", followingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", is(true)));
        }

        @Test
        @DisplayName("GET /api/follows/following/status/{followingId} - Không theo dõi - Trả về false")
        void checkFollowingStatus_isNotFollowing_returnsFalse() throws Exception {
            Long followingId = 3L;
            when(followService.isFollowing(any(Authentication.class), eq(followingId))).thenReturn(false);

            mockMvc.perform(get("/api/follows/following/status/{followingId}", followingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", is(false)));
        }
    }
}
