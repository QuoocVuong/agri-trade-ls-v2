package com.yourcompany.agritradels.usermanagement.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

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