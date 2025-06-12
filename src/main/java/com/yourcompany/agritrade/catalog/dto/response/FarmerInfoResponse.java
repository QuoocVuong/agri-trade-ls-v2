package com.yourcompany.agritrade.catalog.dto.response;

import lombok.Data;

@Data
public class FarmerInfoResponse {
  private Long farmerId; // ID của User (Farmer)
  private String farmName; // Tên trang trại/cửa hàng
  private String fullName; // Tên đầy đủ của User
  private String farmerAvatarUrl; // URL avatar
  private String provinceCode; //  MÃ TỈNH CỦA FARMER
}
