package com.yourcompany.agritradels.interaction.service;

import com.yourcompany.agritradels.interaction.dto.response.FollowUserResponse;
import org.springframework.data.domain.Page; // Import Page
import org.springframework.data.domain.Pageable; // Import Pageable
import org.springframework.security.core.Authentication;

public interface FollowService {

    /**
     * User hiện tại bắt đầu theo dõi một user khác (thường là Farmer).
     * @param authentication Thông tin user hiện tại (follower).
     * @param followingId ID của user muốn theo dõi.
     */
    void followUser(Authentication authentication, Long followingId);

    /**
     * User hiện tại hủy theo dõi một user khác.
     * @param authentication Thông tin user hiện tại (follower).
     * @param followingId ID của user muốn hủy theo dõi.
     */
    void unfollowUser(Authentication authentication, Long followingId);

    /**
     * Lấy danh sách những người mà user hiện tại đang theo dõi (following).
     * @param authentication Thông tin user hiện tại.
     * @param pageable Phân trang.
     * @return Trang danh sách người đang theo dõi.
     */
    Page<FollowUserResponse> getFollowing(Authentication authentication, Pageable pageable);

    /**
     * Lấy danh sách những người đang theo dõi user hiện tại (followers).
     * @param authentication Thông tin user hiện tại.
     * @param pageable Phân trang.
     * @return Trang danh sách người theo dõi.
     */
    Page<FollowUserResponse> getFollowers(Authentication authentication, Pageable pageable);

    /**
     * Lấy danh sách những người đang theo dõi một user cụ thể (public).
     * @param userId ID của user muốn xem followers.
     * @param pageable Phân trang.
     * @return Trang danh sách người theo dõi.
     */
    Page<FollowUserResponse> getFollowersPublic(Long userId, Pageable pageable);


    /**
     * Kiểm tra xem user hiện tại có đang follow user khác không.
     * @param authentication User hiện tại.
     * @param followingId User muốn kiểm tra.
     * @return true nếu đang follow, false nếu không.
     */
    boolean isFollowing(Authentication authentication, Long followingId);
}