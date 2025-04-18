package com.yourcompany.agritradels.catalog.dto.request;

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

    @Size(max = 512)
    private String imageUrl;

    private Integer parentId; // ID của danh mục cha (null nếu là gốc)
}