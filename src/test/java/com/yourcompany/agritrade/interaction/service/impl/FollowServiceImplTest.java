package com.yourcompany.agritrade.interaction.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.interaction.domain.UserFollow;
import com.yourcompany.agritrade.interaction.dto.response.FollowUserResponse;
import com.yourcompany.agritrade.interaction.mapper.FollowUserMapper;
import com.yourcompany.agritrade.interaction.repository.UserFollowRepository;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

  @Mock private UserFollowRepository userFollowRepository;
  @Mock private UserRepository userRepository;
  @Mock private FollowUserMapper followUserMapper;
  @Mock private NotificationService notificationService;
  @Mock private Authentication authentication;

  @InjectMocks private FollowServiceImpl followService;

  private User currentUser, userToFollow, anotherUser;

  @BeforeEach
  void setUp() {
    currentUser = new User();
    currentUser.setId(1L);
    currentUser.setEmail("current@example.com");
    currentUser.setFullName("Current User");
    currentUser.setFollowingCount(0); // Khởi tạo
    currentUser.setFollowerCount(0); // Khởi tạo

    userToFollow = new User();
    userToFollow.setId(2L);
    userToFollow.setEmail("tofollow@example.com");
    userToFollow.setFullName("User To Follow");
    userToFollow.setFollowingCount(0);
    userToFollow.setFollowerCount(0);

    anotherUser = new User();
    anotherUser.setId(3L);
    anotherUser.setEmail("another@example.com");
    anotherUser.setFullName("Another User");

    // Mock chung cho authentication
    lenient().when(authentication.getName()).thenReturn(currentUser.getEmail());
    lenient().when(authentication.isAuthenticated()).thenReturn(true);
    lenient()
        .when(userRepository.findByEmail(currentUser.getEmail()))
        .thenReturn(Optional.of(currentUser));
  }

  @Nested
  @DisplayName("Follow User Tests")
  class FollowUserTests {
    @Test
    @DisplayName("Follow User - Success")
    void followUser_success() {
      when(userRepository.findById(userToFollow.getId())).thenReturn(Optional.of(userToFollow));
      when(userFollowRepository.existsByFollowerIdAndFollowingId(
              currentUser.getId(), userToFollow.getId()))
          .thenReturn(false);
      when(userFollowRepository.save(any(UserFollow.class))).thenAnswer(inv -> inv.getArgument(0));
      // Mock cho updateFollowCounts
      when(userRepository.findById(currentUser.getId()))
          .thenReturn(Optional.of(currentUser)); // Cho follower
      when(userRepository.findById(userToFollow.getId()))
          .thenReturn(Optional.of(userToFollow)); // Cho following
      when(userFollowRepository.countByFollowerId(currentUser.getId()))
          .thenReturn(1L); // Sau khi follow
      when(userFollowRepository.countByFollowingId(userToFollow.getId()))
          .thenReturn(1L); // Sau khi được follow
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      doNothing().when(notificationService).sendNewFollowerNotification(userToFollow, currentUser);

      followService.followUser(authentication, userToFollow.getId());

      ArgumentCaptor<UserFollow> userFollowCaptor = ArgumentCaptor.forClass(UserFollow.class);
      verify(userFollowRepository).save(userFollowCaptor.capture());
      assertEquals(currentUser, userFollowCaptor.getValue().getFollower());
      assertEquals(userToFollow, userFollowCaptor.getValue().getFollowing());

      // Kiểm tra updateFollowCounts đã được gọi và cập nhật đúng
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository, times(2)).save(userCaptor.capture());
      List<User> savedUsers = userCaptor.getAllValues();
      // Thứ tự save có thể không đảm bảo, nên kiểm tra cả hai
      assertTrue(
          savedUsers.stream()
              .anyMatch(u -> u.getId().equals(currentUser.getId()) && u.getFollowingCount() == 1));
      assertTrue(
          savedUsers.stream()
              .anyMatch(u -> u.getId().equals(userToFollow.getId()) && u.getFollowerCount() == 1));

      verify(notificationService).sendNewFollowerNotification(userToFollow, currentUser);
    }

    @Test
    @DisplayName("Follow User - Already Following - Should Do Nothing")
    void followUser_alreadyFollowing_shouldDoNothing() {
      when(userRepository.findById(userToFollow.getId())).thenReturn(Optional.of(userToFollow));
      when(userFollowRepository.existsByFollowerIdAndFollowingId(
              currentUser.getId(), userToFollow.getId()))
          .thenReturn(true);

      followService.followUser(authentication, userToFollow.getId());

      verify(userFollowRepository, never()).save(any(UserFollow.class));
      verify(userRepository, never()).save(any(User.class)); // Không cập nhật count
      verify(notificationService, never()).sendNewFollowerNotification(any(), any());
    }

    @Test
    @DisplayName("Follow User - User To Follow Not Found - Throws ResourceNotFoundException")
    void followUser_userToFollowNotFound_throwsResourceNotFoundException() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());
      assertThrows(
          ResourceNotFoundException.class, () -> followService.followUser(authentication, 99L));
    }
  }

  @Nested
  @DisplayName("Unfollow User Tests")
  class UnfollowUserTests {
    @Test
    @DisplayName("Unfollow User - Success")
    void unfollowUser_success() {
      when(userFollowRepository.existsByFollowerIdAndFollowingId(
              currentUser.getId(), userToFollow.getId()))
          .thenReturn(true);
      doNothing()
          .when(userFollowRepository)
          .deleteByFollowerIdAndFollowingId(currentUser.getId(), userToFollow.getId());
      // Mock cho updateFollowCounts
      when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(currentUser));
      when(userRepository.findById(userToFollow.getId())).thenReturn(Optional.of(userToFollow));
      when(userFollowRepository.countByFollowerId(currentUser.getId()))
          .thenReturn(0L); // Sau khi unfollow
      when(userFollowRepository.countByFollowingId(userToFollow.getId()))
          .thenReturn(0L); // Sau khi bị unfollow
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      followService.unfollowUser(authentication, userToFollow.getId());

      verify(userFollowRepository)
          .deleteByFollowerIdAndFollowingId(currentUser.getId(), userToFollow.getId());
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository, times(2)).save(userCaptor.capture());
      List<User> savedUsers = userCaptor.getAllValues();
      assertTrue(
          savedUsers.stream()
              .anyMatch(u -> u.getId().equals(currentUser.getId()) && u.getFollowingCount() == 0));
      assertTrue(
          savedUsers.stream()
              .anyMatch(u -> u.getId().equals(userToFollow.getId()) && u.getFollowerCount() == 0));
    }

    @Test
    @DisplayName("Unfollow User - Not Following - Should Do Nothing")
    void unfollowUser_notFollowing_shouldDoNothing() {
      when(userFollowRepository.existsByFollowerIdAndFollowingId(
              currentUser.getId(), userToFollow.getId()))
          .thenReturn(false);

      followService.unfollowUser(authentication, userToFollow.getId());

      verify(userFollowRepository, never()).deleteByFollowerIdAndFollowingId(anyLong(), anyLong());
      verify(userRepository, never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("Get Following/Followers Tests")
  class GetFollowsTests {
    @Test
    @DisplayName("Get My Following - Success")
    void getMyFollowing_success() {
      Pageable pageable = PageRequest.of(0, 10);
      List<User> followingUsersList = List.of(userToFollow, anotherUser);
      FollowUserResponse response1 = new FollowUserResponse();
      response1.setUserId(userToFollow.getId());
      FollowUserResponse response2 = new FollowUserResponse();
      response2.setUserId(anotherUser.getId());
      List<FollowUserResponse> expectedResponseList = List.of(response1, response2);

      when(userFollowRepository.findFollowingByFollowerId(currentUser.getId()))
          .thenReturn(followingUsersList);
      when(userFollowRepository.countByFollowerId(currentUser.getId()))
          .thenReturn((long) followingUsersList.size());
      when(followUserMapper.toFollowUserResponseList(followingUsersList))
          .thenReturn(expectedResponseList);

      Page<FollowUserResponse> result = followService.getFollowing(authentication, pageable);

      assertNotNull(result);
      assertEquals(2, result.getTotalElements());
      assertEquals(expectedResponseList, result.getContent());
    }

    @Test
    @DisplayName("Get My Followers - Success")
    void getMyFollowers_success() {
      Pageable pageable = PageRequest.of(0, 10);
      List<User> followerUsersList =
          List.of(anotherUser); // Giả sử anotherUser đang follow currentUser
      FollowUserResponse response1 = new FollowUserResponse();
      response1.setUserId(anotherUser.getId());
      List<FollowUserResponse> expectedResponseList = List.of(response1);

      when(userFollowRepository.findFollowersByFollowingId(currentUser.getId()))
          .thenReturn(followerUsersList);
      when(userFollowRepository.countByFollowingId(currentUser.getId()))
          .thenReturn((long) followerUsersList.size());
      when(followUserMapper.toFollowUserResponseList(followerUsersList))
          .thenReturn(expectedResponseList);

      Page<FollowUserResponse> result = followService.getFollowers(authentication, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(expectedResponseList, result.getContent());
    }

    @Test
    @DisplayName("Get Followers Public - User Exists - Success")
    void getFollowersPublic_userExists_success() {
      Pageable pageable = PageRequest.of(0, 10);
      List<User> followerUsersList =
          List.of(currentUser); // Giả sử currentUser đang follow userToFollow
      FollowUserResponse response1 = new FollowUserResponse();
      response1.setUserId(currentUser.getId());
      List<FollowUserResponse> expectedResponseList = List.of(response1);

      when(userRepository.existsById(userToFollow.getId())).thenReturn(true);
      when(userFollowRepository.findFollowersByFollowingId(userToFollow.getId()))
          .thenReturn(followerUsersList);
      when(userFollowRepository.countByFollowingId(userToFollow.getId()))
          .thenReturn((long) followerUsersList.size());
      when(followUserMapper.toFollowUserResponseList(followerUsersList))
          .thenReturn(expectedResponseList);

      Page<FollowUserResponse> result =
          followService.getFollowersPublic(userToFollow.getId(), pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Get Followers Public - User Not Exists - Throws ResourceNotFoundException")
    void getFollowersPublic_userNotExists_throwsResourceNotFoundException() {
      Pageable pageable = PageRequest.of(0, 10);
      when(userRepository.existsById(99L)).thenReturn(false);
      assertThrows(
          ResourceNotFoundException.class, () -> followService.getFollowersPublic(99L, pageable));
    }
  }

  @Nested
  @DisplayName("Is Following Test")
  class IsFollowingTest {
    @Test
    @DisplayName("Is Following - Returns True")
    void isFollowing_whenFollowing_returnsTrue() {
      when(userFollowRepository.existsByFollowerIdAndFollowingId(
              currentUser.getId(), userToFollow.getId()))
          .thenReturn(true);
      assertTrue(followService.isFollowing(authentication, userToFollow.getId()));
    }

    @Test
    @DisplayName("Is Following - Returns False")
    void isFollowing_whenNotFollowing_returnsFalse() {
      when(userFollowRepository.existsByFollowerIdAndFollowingId(
              currentUser.getId(), anotherUser.getId()))
          .thenReturn(false);
      assertFalse(followService.isFollowing(authentication, anotherUser.getId()));
    }
  }

  @Nested
  @DisplayName("Authentication Helper Tests")
  class AuthenticationHelperTests {
    @Test
    @DisplayName("Get User From Authentication - User Not Found - Throws UsernameNotFoundException")
    void getUserFromAuthentication_whenUserNotFound_shouldThrowUsernameNotFoundException() {
      when(authentication.getName()).thenReturn("unknown@example.com");
      when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
      assertThrows(
          UsernameNotFoundException.class,
          () -> followService.followUser(authentication, userToFollow.getId()));
    }

    @Test
    @DisplayName("Get User From Authentication - Not Authenticated - Throws AccessDeniedException")
    void getUserFromAuthentication_whenNotAuthenticated_shouldThrowAccessDeniedException() {
      when(authentication.isAuthenticated()).thenReturn(false);
      assertThrows(
          AccessDeniedException.class,
          () -> followService.followUser(authentication, userToFollow.getId()));
    }
  }
}
