package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.BusinessProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.BusinessProfileResponse;
import com.yourcompany.agritrade.usermanagement.service.BusinessProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessProfileControllerUnitTest {

    @Mock private BusinessProfileService businessProfileService;
    @Mock private Authentication authentication;

    @InjectMocks
    private BusinessProfileController businessProfileController;

    private BusinessProfileRequest profileRequest;
    private BusinessProfileResponse profileResponseDto;

    @BeforeEach
    void setUp() {
        profileRequest = new BusinessProfileRequest();
        profileRequest.setBusinessName("Global Trade Corp");
        // ... set other fields

        profileResponseDto = new BusinessProfileResponse();
        profileResponseDto.setUserId(1L);
        profileResponseDto.setBusinessName("Global Trade Corp");
        // ... set other fields
    }

    @Test
    void createOrUpdateMyProfile_success_returnsOkWithProfile() {
        when(businessProfileService.createOrUpdateBusinessProfile(authentication, profileRequest)).thenReturn(profileResponseDto);

        ResponseEntity<ApiResponse<BusinessProfileResponse>> responseEntity =
                businessProfileController.createOrUpdateMyProfile(authentication, profileRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(profileResponseDto, responseEntity.getBody().getData());
        assertEquals("Business profile updated successfully", responseEntity.getBody().getMessage());
        verify(businessProfileService).createOrUpdateBusinessProfile(authentication, profileRequest);
    }

    @Test
    void getBusinessProfile_success_returnsOkWithProfile() {
        Long businessUserId = 3L;
        when(businessProfileService.getBusinessProfile(businessUserId)).thenReturn(profileResponseDto);

        ResponseEntity<ApiResponse<BusinessProfileResponse>> responseEntity =
                businessProfileController.getBusinessProfile(businessUserId);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(profileResponseDto, responseEntity.getBody().getData());
        verify(businessProfileService).getBusinessProfile(businessUserId);
    }
}