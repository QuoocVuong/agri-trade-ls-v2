package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.usermanagement.dto.request.GoogleLoginRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2ControllerUnitTest {

    @Mock private UserService userService;

    @InjectMocks
    private OAuth2Controller oauth2Controller;

    private GoogleLoginRequest googleLoginRequest;
    private LoginResponse loginResponse;

    @BeforeEach
    void setUp() {
        googleLoginRequest = new GoogleLoginRequest();
        googleLoginRequest.setIdToken("test-google-id-token");

        UserResponse userResponse = new UserResponse(); /* ... khởi tạo ... */
        loginResponse = new LoginResponse("access", "refresh", userResponse);
    }



    @Test
    void verifyGoogleToken_serviceThrowsBadRequest_returnsBadRequest() throws GeneralSecurityException, IOException {
        when(userService.processGoogleLogin("test-google-id-token"))
                .thenThrow(new BadRequestException("Invalid Google Token"));

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = oauth2Controller.verifyGoogleToken(googleLoginRequest);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertFalse(responseEntity.getBody().isSuccess());
        assertTrue(responseEntity.getBody().getMessage().contains("Invalid Google Token"));
    }

    @Test
    void verifyGoogleToken_serviceThrowsGeneralSecurityException_returnsBadRequest() throws GeneralSecurityException, IOException {
        when(userService.processGoogleLogin("test-google-id-token"))
                .thenThrow(new GeneralSecurityException("Verification failed"));

        ResponseEntity<ApiResponse<LoginResponse>> responseEntity = oauth2Controller.verifyGoogleToken(googleLoginRequest);
        // Controller hiện tại đang bắt Exception chung và trả về BadRequest
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertFalse(responseEntity.getBody().isSuccess());
        assertTrue(responseEntity.getBody().getMessage().contains("Verification failed"));
    }
}