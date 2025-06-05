package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.usermanagement.dto.request.FarmerProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerProfileResponse;
import org.springframework.security.core.Authentication;

public interface FarmerProfileService {
  FarmerProfileResponse createOrUpdateFarmerProfile(
      Authentication authentication, FarmerProfileRequest request);

  FarmerProfileResponse getFarmerProfile(
      Long userId); // Lấy profile của farmer bất kỳ (cho public view)

}
