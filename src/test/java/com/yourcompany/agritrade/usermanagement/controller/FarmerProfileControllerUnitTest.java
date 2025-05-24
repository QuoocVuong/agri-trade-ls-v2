package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.FarmerProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerProfileResponse;
import com.yourcompany.agritrade.usermanagement.service.FarmerProfileService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmerProfileControllerUnitTest {

    @Mock private FarmerProfileService farmerProfileService;
    @Mock private Authentication authentication;

    @InjectMocks
    private FarmerProfileController farmerProfileController;

    private FarmerProfileRequest profileRequest;
    private FarmerProfileResponse profileResponseDto;

    @BeforeEach
    void setUp() {
        profileRequest = new FarmerProfileRequest();
        profileRequest.setFarmName("Green Valley Farm");
        // ... set other fields

        profileResponseDto = new FarmerProfileResponse();
        profileResponseDto.setUserId(1L);
        profileResponseDto.setFarmName("Green Valley Farm");
        // ... set other fields
    }

    @Test
    void createOrUpdateMyProfile_success_returnsOkWithProfile() {
        when(farmerProfileService.createOrUpdateFarmerProfile(authentication, profileRequest)).thenReturn(profileResponseDto);

        ResponseEntity<ApiResponse<FarmerProfileResponse>> responseEntity =
                farmerProfileController.createOrUpdateMyProfile(authentication, profileRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(profileResponseDto, responseEntity.getBody().getData());
        assertEquals("Farmer profile updated successfully", responseEntity.getBody().getMessage());
        verify(farmerProfileService).createOrUpdateFarmerProfile(authentication, profileRequest);
    }

    @Test
    void getFarmerProfile_success_returnsOkWithProfile() {
        Long farmerUserId = 2L;
        when(farmerProfileService.getFarmerProfile(farmerUserId)).thenReturn(profileResponseDto);

        ResponseEntity<ApiResponse<FarmerProfileResponse>> responseEntity =
                farmerProfileController.getFarmerProfile(farmerUserId);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(profileResponseDto, responseEntity.getBody().getData());
        verify(farmerProfileService).getFarmerProfile(farmerUserId);
    }
}