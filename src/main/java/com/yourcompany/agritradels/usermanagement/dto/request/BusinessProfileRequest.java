package com.yourcompany.agritradels.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessProfileRequest {
    @NotBlank(message = "Business name is required")
    @Size(max = 255)
    private String businessName;

    @Size(max = 20)
    private String taxCode;

    @Size(max = 100)
    private String industry;

    @Size(max = 20)
    private String businessPhone;

    @Size(max = 255)
    private String businessAddressDetail;

    @NotBlank(message = "Province code is required")
    @Size(max = 10)
    private String businessProvinceCode;

    @Size(max = 10)
    private String businessDistrictCode;

    @Size(max = 10)
    private String businessWardCode;

    @Size(max = 100)
    private String contactPerson;
}