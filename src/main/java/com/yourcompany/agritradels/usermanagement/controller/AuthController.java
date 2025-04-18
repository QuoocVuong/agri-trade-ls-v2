package com.yourcompany.agritradels.usermanagement.controller;

import com.yourcompany.agritradels.common.dto.ApiResponse;
import com.yourcompany.agritradels.usermanagement.domain.User;
// Cập nhật import cho DTOs
import com.yourcompany.agritradels.usermanagement.dto.request.ForgotPasswordRequest;
import com.yourcompany.agritradels.usermanagement.dto.request.ResetPasswordRequest;
import com.yourcompany.agritradels.usermanagement.dto.request.UserLoginRequest;
import com.yourcompany.agritradels.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritradels.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritradels.usermanagement.mapper.UserMapper;
import com.yourcompany.agritradels.usermanagement.repository.UserRepository;
import com.yourcompany.agritradels.usermanagement.service.UserService;
import com.yourcompany.agritradels.config.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> registerUser(@Valid @RequestBody UserRegistrationRequest registrationRequest) {
        UserResponse createdUser = userService.registerUser(registrationRequest);
        ApiResponse<UserResponse> response = ApiResponse.created(createdUser, "Registration successful. Please check your email to verify your account."); // Thay đổi message
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Endpoint xác thực email (thường là GET vì user click link)
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token) {
        boolean verified = userService.verifyEmail(token);
        if (verified) {
            // Có thể redirect sang trang login của frontend hoặc trả về success
            return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now login."));
        } else {
            // Trường hợp này ít xảy ra nếu service ném exception
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.badRequest("Email verification failed."));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> authenticateUser(@Valid @RequestBody UserLoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));
        UserResponse userResponse = userMapper.toUserResponse(user);
        LoginResponse loginResponseData = new LoginResponse(jwt, userResponse);
        ApiResponse<LoginResponse> response = ApiResponse.success(loginResponseData, "Login successful");
        return ResponseEntity.ok(response);
    }

    // Endpoint yêu cầu quên mật khẩu
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        userService.initiatePasswordReset(forgotPasswordRequest.getEmail());
        // Luôn trả về thành công để tránh lộ thông tin email
        return ResponseEntity.ok(ApiResponse.success("If an account with that email exists, a password reset link has been sent."));
    }

    // Endpoint thực hiện reset mật khẩu
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        userService.resetPassword(resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully."));
    }

}