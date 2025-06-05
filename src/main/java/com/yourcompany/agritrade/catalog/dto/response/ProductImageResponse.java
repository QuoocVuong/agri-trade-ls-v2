package com.yourcompany.agritrade.catalog.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProductImageResponse {
  private Long id;
  private String imageUrl; // Sẽ là Signed URL được tạo động
  private String blobPath;
  // để Frontend có thể dùng
  private boolean isDefault;
  private int displayOrder;
  private LocalDateTime createdAt;
}
