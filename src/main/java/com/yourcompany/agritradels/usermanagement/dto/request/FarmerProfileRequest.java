package com.yourcompany.agritradels.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class FarmerProfileRequest {
    @NotBlank(message = "Farm name is required")
    @Size(max = 255)
    private String farmName;

    private String description;

    @Size(max = 255)
    private String addressDetail;

    @NotBlank(message = "Province code is required")
    @Size(max = 10)
    private String provinceCode;

    @Size(max = 10)
    private String districtCode;

    @Size(max = 10)
    private String wardCode;

    @Size(max = 512)
    private String coverImageUrl;

    @NotNull // Mặc định là false nếu không gửi
    private Boolean canSupplyB2b = false;

    private String b2bCertifications;

    private BigDecimal minB2bOrderValue;
}