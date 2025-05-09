package com.yourcompany.agritrade.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    @Size(max = 100)
    private String name;

    // Slug có thể để trống, backend sẽ tự tạo nếu trống
    @Size(max = 120)
    private String slug;

    private String description;

    // URL preview (Signed URL từ upload) - Có thể optional nếu không bắt buộc ảnh
    @Size(max = 2048) // Tăng độ dài cho Signed URL
    private String imageUrl;

    // Path trên storage (fileName từ FileUploadResponse) - Có thể optional
    @Size(max = 1024)
    private String blobPath;

    private Integer parentId; // ID của danh mục cha (null nếu là gốc)
}