package com.yourcompany.agritradels.catalog.dto.response;

import lombok.Data;

// DTO đơn giản để nhúng vào Product
@Data
public class FarmerInfoResponse {
    private Long farmerId; // Chính là userId
    private String farmName;
    private String farmerAvatarUrl; // Lấy từ User entity
    private String provinceCode; // Lấy từ FarmerProfile
}