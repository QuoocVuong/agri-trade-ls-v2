package com.yourcompany.agritrade.catalog.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProductImageResponse {
  private Long id;
  private String imageUrl; // Sẽ là Signed URL được tạo động
  private String blobPath; // <<<< NÊN THÊM trường này vào DTO response
  // để Frontend có thể dùng nếu cần (ví dụ: khi sửa, để biết path nào cần xóa)
  private boolean isDefault;
  private int displayOrder; // Thêm trường thứ tự
  private LocalDateTime createdAt;
}
