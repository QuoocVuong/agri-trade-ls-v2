package com.yourcompany.agritrade.catalog.dto.response;

import lombok.Data;

// DTO đơn giản để nhúng vào Product
@Data
public class FarmerInfoResponse {
  private Long farmerId; // ID của User (Farmer)
  private String farmName; // Tên trang trại/cửa hàng (có thể null)
  private String fullName; // Tên đầy đủ của User
  private String farmerAvatarUrl; // URL avatar
  private String provinceCode; // *** THÊM MÃ TỈNH CỦA FARMER ***
  // Thêm các trường khác nếu cần (ví dụ: rating của farmer...)
}
