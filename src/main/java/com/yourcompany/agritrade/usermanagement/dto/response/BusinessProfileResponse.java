package com.yourcompany.agritrade.usermanagement.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BusinessProfileResponse {
  private Long userId;
  private String businessName;
  private String taxCode;
  private String industry;
  private String businessPhone;
  private String businessAddressDetail;
  private String businessProvinceCode;
  private String districtCode;
  private String wardCode;
  private String contactPerson;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
