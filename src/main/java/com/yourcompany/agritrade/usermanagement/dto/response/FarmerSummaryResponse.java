package com.yourcompany.agritrade.usermanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO chứa thông tin tóm tắt của Farmer để hiển thị trong danh sách công khai. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FarmerSummaryResponse {
  private Long userId;
  private String farmName; // Lấy từ FarmerProfile
  private String fullName; // Lấy từ User
  private String avatarUrl; // Lấy từ User
  private String provinceCode; // Lấy từ FarmerProfile
  private Integer followerCount; // Thêm để kiểm tra sắp xếp (tùy chọn)
  // Thêm các trường khác nếu cần (rating,...)
}
