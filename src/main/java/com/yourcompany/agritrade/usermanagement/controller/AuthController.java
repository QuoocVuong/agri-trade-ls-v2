package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.config.security.TokenBlacklistService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.ForgotPasswordRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.ResetPasswordRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserLoginRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final JwtTokenProvider tokenProvider;
  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final TokenBlacklistService tokenBlacklistService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<UserResponse>> registerUser(
      @Valid @RequestBody UserRegistrationRequest registrationRequest) {
    UserResponse createdUser = userService.registerUser(registrationRequest);
    ApiResponse<UserResponse> response =
        ApiResponse.created(
            createdUser,
            "Registration successful. Please check your email to verify your account."); // Thay đổi
    // message
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // Endpoint xác thực email (thường là GET vì user click link)
  @GetMapping("/verify")
  public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token) {
    boolean verified = userService.verifyEmail(token);
    if (verified) {
      // Có thể redirect sang trang login của frontend hoặc trả về success
      return ResponseEntity.ok(
          ApiResponse.success("Email verified successfully. You can now login."));
    } else {
      // Trường hợp này ít xảy ra nếu service ném exception
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.badRequest("Email verification failed."));
    }
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> authenticateUser(
      @Valid @RequestBody UserLoginRequest loginRequest) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword()));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    String jwt = tokenProvider.generateAccessToken(authentication);
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    User user =
        userRepository
            .findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found after authentication"));
    UserResponse userResponse = userMapper.toUserResponse(user);
    LoginResponse loginResponseData = userService.processLoginAuthentication(authentication);
    ApiResponse<LoginResponse> response =
        ApiResponse.success(loginResponseData, "Login successful");
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
      @RequestBody String refreshToken) { // Nhận refresh token từ body
    // Loại bỏ dấu ngoặc kép thừa nếu client gửi dạng JSON string: "\"your_token\""
    String cleanRefreshToken = refreshToken.replace("\"", "");
    LoginResponse newTokens = userService.refreshToken(cleanRefreshToken);
    return ResponseEntity.ok(ApiResponse.success(newTokens, "Token refreshed successfully"));
  }

  @PostMapping("/logout")
  @PreAuthorize("isAuthenticated()") // Chỉ user đã đăng nhập mới có thể logout
  public ResponseEntity<ApiResponse<Void>> logoutUser(HttpServletRequest request) {
    String jwt = getJwtFromRequest(request); // Dùng lại helper method

    if (StringUtils.hasText(jwt)) {
      String jti = tokenProvider.getJtiFromToken(jwt);
      Date expiryDate = tokenProvider.getExpiryDateFromToken(jwt);

      if (jti != null && expiryDate != null) {
        tokenBlacklistService.addToBlacklist(jti, expiryDate);
        log.info(
            "User {} logged out. Token JTI {} added to blacklist.",
            SecurityContextHolder.getContext().getAuthentication().getName(),
            jti);
      } else {
        log.warn(
            "Could not add token to blacklist: JTI or ExpiryDate is null for the provided token.");
      }
    } else {
      log.warn("Logout attempt without a JWT token in Authorization header.");
    }

    String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
    userService.invalidateRefreshTokenForUser(userEmail); // << THÊM HÀM NÀY VÀO UserService

    SecurityContextHolder.clearContext(); // Luôn xóa context bảo mật
    return ResponseEntity.ok(ApiResponse.success("Logout successful. Token has been invalidated."));
  }

  // Helper method để lấy token từ header (có thể đặt ở đây hoặc trong JwtAuthenticationFilter)
  private String getJwtFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7); // "Bearer ".length() == 7
    }
    return null;
  }

  // Endpoint yêu cầu quên mật khẩu
  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
    userService.initiatePasswordReset(forgotPasswordRequest.getEmail());
    // Luôn trả về thành công để tránh lộ thông tin email
    return ResponseEntity.ok(
        ApiResponse.success(
            "If an account with that email exists, a password reset link has been sent."));
  }

  // Endpoint thực hiện reset mật khẩu
  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
    userService.resetPassword(
        resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());
    return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully."));
  }
}
