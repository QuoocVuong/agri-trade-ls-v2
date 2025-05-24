package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.config.security.TokenBlacklistService;
import com.yourcompany.agritrade.usermanagement.dto.request.ForgotPasswordRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserLoginRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock private UserService userService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private Authentication authentication;

    @InjectMocks
    private AuthController authController;

    private UserRegistrationRequest registrationRequest;
    private UserResponse userResponse;
    private UserLoginRequest loginRequest;
    private LoginResponse loginResponseData;

    @BeforeEach
    void setUp() {
        registrationRequest = new UserRegistrationRequest();
        registrationRequest.setEmail("test@example.com");
        registrationRequest.setPassword("password");
        registrationRequest.setFullName("Test User");

        userResponse = new UserResponse(); /* ... khởi tạo ... */
        loginRequest = new UserLoginRequest(); /* ... khởi tạo ... */
        loginResponseData = new LoginResponse("access", "refresh", userResponse);

        SecurityContextHolder.clearContext(); // Đảm bảo context sạch
    }

    @Test
    void registerUser_success_returnsCreated() {
        when(userService.registerUser(any(UserRegistrationRequest.class))).thenReturn(userResponse);

        ResponseEntity<ApiResponse<UserResponse>> responseEntity = authController.registerUser(registrationRequest);

        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userResponse, responseEntity.getBody().getData());
        verify(userService).registerUser(registrationRequest);
    }

    @Test
    void registerUser_serviceThrowsBadRequest_returnsBadRequest() {
        when(userService.registerUser(any(UserRegistrationRequest.class)))
                .thenThrow(new BadRequestException("Email taken"));

        ResponseEntity<ApiResponse<UserResponse>> responseEntity = authController.registerUser(registrationRequest);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertFalse(responseEntity.getBody().isSuccess());
        assertEquals("Email taken", responseEntity.getBody().getMessage());
    }

    @Test
    void authenticateUser_success_returnsOkWithLoginResponse() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userService.processLoginAuthentication(authentication)).thenReturn(loginResponseData);

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.authenticateUser(loginRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(loginResponseData, responseEntity.getBody().getData());
        assertEquals(authentication, SecurityContextHolder.getContext().getAuthentication()); // Kiểm tra context được set
    }

    @Test
    void authenticateUser_badCredentials_returnsUnauthorized() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.authenticateUser(loginRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
        assertFalse(responseEntity.getBody().isSuccess());
        assertEquals("Invalid email or password.", responseEntity.getBody().getMessage());
    }

    @Test
    void refreshToken_success_returnsOkWithNewTokens() {
        String refreshTokenString = "\"some-refresh-token\"";
        String cleanRefreshToken = "some-refresh-token";
        when(userService.refreshToken(cleanRefreshToken)).thenReturn(loginResponseData);

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.refreshToken(refreshTokenString);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(loginResponseData, responseEntity.getBody().getData());
        verify(userService).refreshToken(cleanRefreshToken);
    }

    @Test
    void logoutUser_success_returnsOkAndInvalidatesToken() {
        String jwt = "dummy.jwt.token";
        String jti = "jwtId";
        String userEmail = "user@example.com";

        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + jwt);
        when(tokenProvider.getJtiFromToken(jwt)).thenReturn(jti);
        when(tokenProvider.getExpiryDateFromToken(jwt)).thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
        when(authentication.getName()).thenReturn(userEmail); // Mock cho SecurityContextHolder
        SecurityContextHolder.getContext().setAuthentication(authentication); // Giả lập user đã đăng nhập

        ResponseEntity<ApiResponse<Void>> responseEntity = authController.logoutUser(httpServletRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        verify(tokenBlacklistService).addToBlacklist(eq(jti), any(java.util.Date.class));
        verify(userService).invalidateRefreshTokenForUser(userEmail);
        assertNull(SecurityContextHolder.getContext().getAuthentication()); // Context đã được clear
    }


    @Test
    void forgotPassword_callsServiceAndReturnsOk() {
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("user@example.com");
        doNothing().when(userService).initiatePasswordReset("user@example.com");

        ResponseEntity<ApiResponse<Void>> responseEntity = authController.forgotPassword(forgotRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        verify(userService).initiatePasswordReset("user@example.com");
    }

    // TODO: Thêm test cho verifyEmail, resetPassword, và các kịch bản lỗi khác
}