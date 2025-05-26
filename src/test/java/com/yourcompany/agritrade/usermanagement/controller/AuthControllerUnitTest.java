package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.config.security.TokenBlacklistService;
import com.yourcompany.agritrade.usermanagement.dto.request.ForgotPasswordRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.ResetPasswordRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserLoginRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;


import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthController authController;

    private UserRegistrationRequest registrationRequest;
    private UserResponse userResponseDto;
    private UserLoginRequest loginRequest;
    private LoginResponse loginResponseData;
    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        registrationRequest = new UserRegistrationRequest();
        registrationRequest.setEmail("test@example.com");
        registrationRequest.setPassword("password123");
        registrationRequest.setFullName("Test User");
        registrationRequest.setPhoneNumber("0123456789");

        userResponseDto = new UserResponse();
        userResponseDto.setId(1L);
        userResponseDto.setEmail("test@example.com");
        userResponseDto.setFullName("Test User");
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_CONSUMER");
        userResponseDto.setRoles(roles);


        loginRequest = new UserLoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        UserResponse userInLoginResponse = new UserResponse();
        userInLoginResponse.setId(1L);
        userInLoginResponse.setEmail("test@example.com");
        userInLoginResponse.setFullName("Test User");
        userInLoginResponse.setRoles(roles);

        loginResponseData = new LoginResponse("sampleAccessToken", "sampleRefreshToken", userInLoginResponse);

        mockUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .build();

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Register User - Success - Returns 201 Created")
    void registerUser_success_returnsCreated() {
        when(userService.registerUser(any(UserRegistrationRequest.class))).thenReturn(userResponseDto);
        ResponseEntity<ApiResponse<UserResponse>> responseEntity = authController.registerUser(registrationRequest);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(userResponseDto, responseEntity.getBody().getData());
    }



    @Test
    @DisplayName("Verify Email - Success - Returns 200 OK")
    void verifyEmail_success_returnsOk() {
        String validToken = "valid-verification-token";
        when(userService.verifyEmail(validToken)).thenReturn(true);
        ResponseEntity<ApiResponse<Void>> responseEntity = authController.verifyEmail(validToken);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
    }








    @Test
    @DisplayName("Refresh Token - Success - Returns 200 OK with New Tokens")
    void refreshToken_success_returnsOkWithNewTokens() {
        String refreshTokenStringWithQuotes = "\"some-refresh-token\"";
        String cleanRefreshToken = "some-refresh-token";
        when(userService.refreshToken(cleanRefreshToken)).thenReturn(loginResponseData);
        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = authController.refreshToken(refreshTokenStringWithQuotes);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(loginResponseData, responseEntity.getBody().getData());
    }

    @Test
    @DisplayName("Logout User - Success - Returns 200 OK and Invalidates Token")
    void logoutUser_success_returnsOkAndInvalidatesToken() {
        String jwt = "dummy.jwt.token";
        String jti = "jwtId123";
        Date expiry = new Date(System.currentTimeMillis() + 3600000);
        String userEmail = "user@example.com";

        when(authentication.getName()).thenReturn(userEmail);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + jwt);
        when(tokenProvider.getJtiFromToken(jwt)).thenReturn(jti);
        when(tokenProvider.getExpiryDateFromToken(jwt)).thenReturn(expiry);

        ResponseEntity<ApiResponse<Void>> responseEntity = authController.logoutUser(httpServletRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        verify(tokenBlacklistService).addToBlacklist(jti, expiry);
        verify(userService).invalidateRefreshTokenForUser(userEmail);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Forgot Password - Calls Service and Returns 200 OK")
    void forgotPassword_callsServiceAndReturnsOk() {
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("user@example.com");
        doNothing().when(userService).initiatePasswordReset("user@example.com");
        ResponseEntity<ApiResponse<Void>> responseEntity = authController.forgotPassword(forgotRequest);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
    }

    @Test
    @DisplayName("Reset Password - Success - Returns 200 OK")
    void resetPassword_success_returnsOk() {
        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken("reset-token");
        resetRequest.setNewPassword("newStrongPassword");
        doNothing().when(userService).resetPassword("reset-token", "newStrongPassword");
        ResponseEntity<ApiResponse<Void>> responseEntity = authController.resetPassword(resetRequest);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
    }

}