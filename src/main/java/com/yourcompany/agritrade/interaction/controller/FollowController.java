package com.yourcompany.agritrade.interaction.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.interaction.dto.response.FollowUserResponse;
import com.yourcompany.agritrade.interaction.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // Theo dõi một user
    @PostMapping("/following/{followingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> followUser(Authentication authentication, @PathVariable Long followingId) {
        followService.followUser(authentication, followingId);
        return ResponseEntity.ok(ApiResponse.success("User followed successfully"));
    }

    // Hủy theo dõi một user
    @DeleteMapping("/following/{followingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unfollowUser(Authentication authentication, @PathVariable Long followingId) {
        followService.unfollowUser(authentication, followingId);
        return ResponseEntity.ok(ApiResponse.success("User unfollowed successfully"));
    }

    // Lấy danh sách người mình đang theo dõi
    @GetMapping("/following/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<FollowUserResponse>>> getMyFollowing(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FollowUserResponse> following = followService.getFollowing(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(following));
    }

    // Lấy danh sách người đang theo dõi mình
    @GetMapping("/followers/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<FollowUserResponse>>> getMyFollowers(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FollowUserResponse> followers = followService.getFollowers(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(followers));
    }

    // Lấy danh sách người theo dõi của user khác (public)
    @GetMapping("/followers/user/{userId}")
    // Có thể không cần PreAuthorize nếu muốn public
    public ResponseEntity<ApiResponse<Page<FollowUserResponse>>> getFollowersPublic(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FollowUserResponse> followers = followService.getFollowersPublic(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(followers));
    }

    // Kiểm tra xem mình có đang follow user khác không
    @GetMapping("/following/status/{followingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> checkFollowingStatus(
            Authentication authentication, @PathVariable Long followingId) {
        boolean isFollowing = followService.isFollowing(authentication, followingId);
        return ResponseEntity.ok(ApiResponse.success(isFollowing));
    }
}