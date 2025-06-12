package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.interaction.domain.UserFollow;
import com.yourcompany.agritrade.interaction.dto.response.FollowUserResponse;
import com.yourcompany.agritrade.interaction.mapper.FollowUserMapper;
import com.yourcompany.agritrade.interaction.repository.UserFollowRepository;
import com.yourcompany.agritrade.interaction.service.FollowService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl implements FollowService {

  private final UserFollowRepository userFollowRepository;
  private final UserRepository userRepository;
  private final FollowUserMapper followUserMapper;
  private final NotificationService notificationService;

  @Override
  @Transactional
  public void followUser(Authentication authentication, Long followingId) {
    User follower = getUserFromAuthentication(authentication);
    User following =
        userRepository
            .findById(followingId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", followingId));

    if (follower.getId().equals(followingId)) {
      throw new BadRequestException("You cannot follow yourself.");
    }

    if (userFollowRepository.existsByFollowerIdAndFollowingId(follower.getId(), followingId)) {
      log.warn("User {} already follows user {}", follower.getId(), followingId);
      // Có thể throw lỗi hoặc không làm gì cả
      // throw new BadRequestException("You are already following this user.");
      return;
    }

    UserFollow userFollow = new UserFollow();
    userFollow.setFollower(follower);
    userFollow.setFollowing(following);
    userFollowRepository.save(userFollow);

    updateFollowCounts(follower.getId(), followingId, true); // Gọi hàm cập nhật count

    log.info("User {} started following user {}", follower.getId(), followingId);
    // Gửi thông báo cho người được follow
    notificationService.sendNewFollowerNotification(following, follower); // Gọi NotificationService
  }

  @Override
  @Transactional
  public void unfollowUser(Authentication authentication, Long followingId) {
    User follower = getUserFromAuthentication(authentication);

    if (!userFollowRepository.existsByFollowerIdAndFollowingId(follower.getId(), followingId)) {
      log.warn("User {} is not following user {}", follower.getId(), followingId);
      // Có thể throw lỗi hoặc không làm gì cả
      // throw new BadRequestException("You are not following this user.");
      return;
    }

    userFollowRepository.deleteByFollowerIdAndFollowingId(follower.getId(), followingId);

    updateFollowCounts(follower.getId(), followingId, false); // Gọi hàm cập nhật count

    log.info("User {} unfollowed user {}", follower.getId(), followingId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<FollowUserResponse> getFollowing(Authentication authentication, Pageable pageable) {
    User follower = getUserFromAuthentication(authentication);
    // Cần tạo phương thức trả về Page<User> trong repository hoặc xử lý phân trang ở đây
    // Ví dụ tạm: Lấy List rồi tạo Page thủ công (không hiệu quả với dữ liệu lớn)
    List<User> followingUsers = userFollowRepository.findFollowingByFollowerId(follower.getId());
    List<FollowUserResponse> responseList =
        followUserMapper.toFollowUserResponseList(followingUsers);
    // Tạo Page thủ công (cần tính toán total elements nếu repo không trả về Page)
    long totalElements = userFollowRepository.countByFollowerId(follower.getId());
    return new PageImpl<>(responseList, pageable, totalElements);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<FollowUserResponse> getFollowers(Authentication authentication, Pageable pageable) {
    User following = getUserFromAuthentication(authentication);
    // Tương tự getFollowing, cần xử lý phân trang
    List<User> followerUsers = userFollowRepository.findFollowersByFollowingId(following.getId());
    List<FollowUserResponse> responseList =
        followUserMapper.toFollowUserResponseList(followerUsers);
    long totalElements = userFollowRepository.countByFollowingId(following.getId());
    return new PageImpl<>(responseList, pageable, totalElements);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<FollowUserResponse> getFollowersPublic(Long userId, Pageable pageable) {
    // Kiểm tra xem user có tồn tại không
    if (!userRepository.existsById(userId)) {
      throw new ResourceNotFoundException("User", "id", userId);
    }
    // Tương tự getFollowers, cần xử lý phân trang
    List<User> followerUsers = userFollowRepository.findFollowersByFollowingId(userId);
    List<FollowUserResponse> responseList =
        followUserMapper.toFollowUserResponseList(followerUsers);
    long totalElements = userFollowRepository.countByFollowingId(userId);
    return new PageImpl<>(responseList, pageable, totalElements);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isFollowing(Authentication authentication, Long followingId) {
    User follower = getUserFromAuthentication(authentication);
    return userFollowRepository.existsByFollowerIdAndFollowingId(follower.getId(), followingId);
  }

  // Helper method (copy từ UserServiceImpl hoặc tách ra Util)
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  private void updateFollowCounts(Long followerId, Long followingId, boolean isFollowing) {
    User follower = userRepository.findById(followerId).orElse(null);
    User following = userRepository.findById(followingId).orElse(null);
    if (follower != null && following != null) {

      // Đếm lại và ép kiểu sang int
      int currentFollowingCount = (int) userFollowRepository.countByFollowerId(followerId);
      int currentFollowerCount = (int) userFollowRepository.countByFollowingId(followingId);

      follower.setFollowingCount(
          currentFollowingCount); // Truyền int vào setter nhận Integer (auto-boxing)
      following.setFollowerCount(currentFollowerCount); // Truyền int vào setter nhận Integer

      userRepository.save(follower);
      userRepository.save(following);
      log.debug(
          "Updated follow counts for follower {} ({}) and following {} ({})",
          followerId,
          currentFollowingCount,
          followingId,
          currentFollowerCount);
    }
  }
}
