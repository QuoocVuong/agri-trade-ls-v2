package com.yourcompany.agritrade.catalog.dto.request;

import lombok.Data;

@Data
public class ProductRejectRequest {
  private String reason; // Có thể thêm validation nếu cần
}
