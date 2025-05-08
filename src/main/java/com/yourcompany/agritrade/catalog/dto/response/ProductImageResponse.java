package com.yourcompany.agritrade.catalog.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductImageResponse {
    private Long id;
    private String imageUrl; // Sẽ là Signed URL được tạo động
    private boolean isDefault;
    private int displayOrder; // Thêm trường thứ tự
    private LocalDateTime createdAt;
}