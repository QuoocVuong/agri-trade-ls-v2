package com.yourcompany.agritrade.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageRequest {
    private Long id; // ID của ảnh (dùng khi cập nhật)

    @NotBlank(message = "Image URL is required")
    @Size(max = 512)
    private String imageUrl; // URL ảnh (sau khi upload)

    @NotNull
    private Boolean isDefault = false;

    @NotNull
    private Integer displayOrder = 0;
}