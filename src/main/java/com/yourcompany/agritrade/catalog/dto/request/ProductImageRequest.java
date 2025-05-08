package com.yourcompany.agritrade.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageRequest {
    private Long id; // ID của ảnh (dùng khi cập nhật)

    @NotBlank(message = "Image URL is required for preview or reference") // Đây sẽ là Signed URL từ upload
    @Size(max = 2048)
    private String imageUrl; // URL ảnh (sau khi upload)

    @NotBlank(message = "Blob path is required") // blobPath (fileName từ FileUploadResponse) là bắt buộc
    @Size(max = 1024)
    private String blobPath; // <<< THÊM TRƯỜNG NÀY

    @NotNull
    private Boolean isDefault = false;

    @NotNull
    private Integer displayOrder = 0;
}