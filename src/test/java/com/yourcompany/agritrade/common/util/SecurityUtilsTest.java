// src/test/java/com/yourcompany/agritrade/common/util/SecurityUtilsTest.java
package com.yourcompany.agritrade.common.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

  @Mock private UserRepository mockUserRepository;
  @Mock private Authentication mockAuthentication;
  @Mock private SecurityContext mockSecurityContext;

  private MockedStatic<SecurityContextHolder> mockedSecurityContextHolder;

  private SecurityUtils securityUtils; // Instance để Spring inject UserRepository

  @BeforeEach
  void setUp() {
    // Tạo instance SecurityUtils với UserRepository đã mock
    // Điều này mô phỏng việc Spring inject UserRepository vào SecurityUtils
    securityUtils = new SecurityUtils(mockUserRepository);

    // Mock SecurityContextHolder để trả về mockSecurityContext
    mockedSecurityContextHolder = Mockito.mockStatic(SecurityContextHolder.class);
    mockedSecurityContextHolder
        .when(SecurityContextHolder::getContext)
        .thenReturn(mockSecurityContext);
  }

  @AfterEach
  void tearDown() {
    // Đóng static mock sau mỗi test
    mockedSecurityContextHolder.close();
  }

  @Test
  void getCurrentAuthenticatedUser_whenAuthenticatedWithUserDetails_returnsUser() {
    String email = "test@example.com";
    User expectedUser = new User();
    expectedUser.setEmail(email);

    UserDetails mockUserDetails = Mockito.mock(UserDetails.class);
    when(mockUserDetails.getUsername()).thenReturn(email);

    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true);
    when(mockAuthentication.getPrincipal()).thenReturn(mockUserDetails);
    when(mockUserRepository.findByEmail(email)).thenReturn(Optional.of(expectedUser));

    User actualUser = SecurityUtils.getCurrentAuthenticatedUser();

    assertSame(expectedUser, actualUser);
  }

  @Test
  void getCurrentAuthenticatedUser_whenAuthenticatedWithStringPrincipal_returnsUser() {
    String email = "test@example.com";
    User expectedUser = new User();
    expectedUser.setEmail(email);

    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true);
    when(mockAuthentication.getPrincipal()).thenReturn(email); // Principal là String
    when(mockUserRepository.findByEmail(email)).thenReturn(Optional.of(expectedUser));

    User actualUser = SecurityUtils.getCurrentAuthenticatedUser();
    assertSame(expectedUser, actualUser);
  }

  @Test
  void getCurrentAuthenticatedUser_whenAuthenticatedWithUserEntityPrincipal_returnsUser() {
    String email = "test@example.com";
    User principalUser = new User();
    principalUser.setEmail(email);
    // User này chính là user mong đợi từ DB
    User expectedUser = principalUser;

    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true);
    when(mockAuthentication.getPrincipal()).thenReturn(principalUser); // Principal là User entity
    when(mockUserRepository.findByEmail(email)).thenReturn(Optional.of(expectedUser));

    User actualUser = SecurityUtils.getCurrentAuthenticatedUser();
    assertSame(expectedUser, actualUser);
  }

  @Test
  void getCurrentAuthenticatedUser_whenNotAuthenticated_throwsAccessDeniedException() {
    when(mockSecurityContext.getAuthentication()).thenReturn(null);
    assertThrows(AccessDeniedException.class, SecurityUtils::getCurrentAuthenticatedUser);
  }

  @Test
  void getCurrentAuthenticatedUser_whenPrincipalIsAnonymous_throwsAccessDeniedException() {
    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true); // Anonymous user is authenticated
    when(mockAuthentication.getPrincipal()).thenReturn("anonymousUser");

    assertThrows(AccessDeniedException.class, SecurityUtils::getCurrentAuthenticatedUser);
  }

  @Test
  void getCurrentAuthenticatedUser_whenUserNotFoundInDb_throwsUsernameNotFoundException() {
    String email = "unknown@example.com";
    UserDetails mockUserDetails = Mockito.mock(UserDetails.class);
    when(mockUserDetails.getUsername()).thenReturn(email);

    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true);
    when(mockAuthentication.getPrincipal()).thenReturn(mockUserDetails);
    when(mockUserRepository.findByEmail(email)).thenReturn(Optional.empty());

    assertThrows(UsernameNotFoundException.class, SecurityUtils::getCurrentAuthenticatedUser);
  }

  @Test
  void getCurrentAuthenticatedUser_whenPrincipalIsUnexpectedType_throwsAccessDeniedException() {
    Object unexpectedPrincipal = new Object();
    when(mockSecurityContext.getAuthentication()).thenReturn(mockAuthentication);
    when(mockAuthentication.isAuthenticated()).thenReturn(true);
    when(mockAuthentication.getPrincipal()).thenReturn(unexpectedPrincipal);

    assertThrows(AccessDeniedException.class, SecurityUtils::getCurrentAuthenticatedUser);
  }
}
