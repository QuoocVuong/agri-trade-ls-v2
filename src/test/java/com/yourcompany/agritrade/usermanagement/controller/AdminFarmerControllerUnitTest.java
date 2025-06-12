package com.yourcompany.agritrade.usermanagement.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.usermanagement.dto.request.FarmerRejectRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.service.AdminUserService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminFarmerControllerUnitTest {

  @Mock private AdminUserService adminUserService;
  @Mock private Authentication authentication; // Mock Authentication cho các phương thức cần nó

  @InjectMocks private AdminFarmerController adminFarmerController;

  private UserProfileResponse userProfileResponse;
  private Page<UserProfileResponse> userProfilePage;

  @BeforeEach
  void setUp() {
    userProfileResponse = new UserProfileResponse();
    userProfileResponse.setId(1L);
    userProfileResponse.setFullName("Test Farmer");
    // ... set các trường khác

    userProfilePage = new PageImpl<>(Collections.singletonList(userProfileResponse));
  }

  @Test
  void getPendingFarmers_success_returnsOkWithPage() {
    Pageable pageable = PageRequest.of(0, 10);
    when(adminUserService.getPendingFarmers(pageable)).thenReturn(userProfilePage);

    ResponseEntity<ApiResponse<Page<UserProfileResponse>>> responseEntity =
        adminFarmerController.getPendingFarmers(pageable);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertTrue(responseEntity.getBody().isSuccess());
    assertEquals(userProfilePage, responseEntity.getBody().getData());
    verify(adminUserService).getPendingFarmers(pageable);
  }

  @Test
  void getAllFarmers_success_returnsOkWithPage() {
    Pageable pageable = PageRequest.of(0, 15);
    VerificationStatus status = VerificationStatus.VERIFIED;
    String keyword = "farm";
    when(adminUserService.getAllFarmers(status, keyword, pageable)).thenReturn(userProfilePage);

    ResponseEntity<ApiResponse<Page<UserProfileResponse>>> responseEntity =
        adminFarmerController.getAllFarmers(status, keyword, pageable);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertTrue(responseEntity.getBody().isSuccess());
    assertEquals(userProfilePage, responseEntity.getBody().getData());
    verify(adminUserService).getAllFarmers(status, keyword, pageable);
  }

  @Test
  void approveFarmer_success_returnsOk() {
    Long farmerUserId = 1L;
    doNothing().when(adminUserService).approveFarmer(farmerUserId, authentication);

    ResponseEntity<ApiResponse<Void>> responseEntity =
        adminFarmerController.approveFarmer(farmerUserId, authentication);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertTrue(responseEntity.getBody().isSuccess());
    assertEquals("Farmer approved successfully.", responseEntity.getBody().getMessage());
    verify(adminUserService).approveFarmer(farmerUserId, authentication);
  }

  @Test
  void rejectFarmer_withReason_success_returnsOk() {
    Long farmerUserId = 1L;
    FarmerRejectRequest rejectRequest = new FarmerRejectRequest();
    rejectRequest.setReason("Not enough documents");
    doNothing()
        .when(adminUserService)
        .rejectFarmer(farmerUserId, "Not enough documents", authentication);

    ResponseEntity<ApiResponse<Void>> responseEntity =
        adminFarmerController.rejectFarmer(farmerUserId, rejectRequest, authentication);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertTrue(responseEntity.getBody().isSuccess());
    assertEquals("Farmer rejected successfully.", responseEntity.getBody().getMessage());
    verify(adminUserService).rejectFarmer(farmerUserId, "Not enough documents", authentication);
  }

  @Test
  void rejectFarmer_withoutReason_success_returnsOk() {
    Long farmerUserId = 1L;
    doNothing().when(adminUserService).rejectFarmer(farmerUserId, null, authentication);

    ResponseEntity<ApiResponse<Void>> responseEntity =
        adminFarmerController.rejectFarmer(farmerUserId, null, authentication); // request là null

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertTrue(responseEntity.getBody().isSuccess());
    assertEquals("Farmer rejected successfully.", responseEntity.getBody().getMessage());
    verify(adminUserService).rejectFarmer(farmerUserId, null, authentication);
  }
}
