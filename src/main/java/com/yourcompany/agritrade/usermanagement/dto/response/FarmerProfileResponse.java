package com.yourcompany.agritrade.usermanagement.dto.response;

import com.yourcompany.agritrade.common.model.VerificationStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FarmerProfileResponse {
    private Long userId;
    private String farmName;
    private String description;
    private String addressDetail;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private String coverImageUrl;
    private VerificationStatus verificationStatus;
    private LocalDateTime verifiedAt;
    private String verifiedByAdminName; // Tên admin duyệt (nếu cần)
    private boolean canSupplyB2b;
    private String b2bCertifications;
    private BigDecimal minB2bOrderValue;


    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private Integer followerCount;



    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}