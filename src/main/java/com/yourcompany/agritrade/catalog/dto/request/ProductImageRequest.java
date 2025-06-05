package com.yourcompany.agritrade.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageRequest {
  private Long id; // ID của ảnh

  @NotBlank(
      message = "Image URL is required for preview or reference")
  @Size(max = 2048)
  private String imageUrl; // URL ảnh

  @NotBlank(
      message = "Blob path is required")
  @Size(max = 1024)
  private String blobPath;

  @NotNull private Boolean isDefault = false;

  @NotNull private Integer displayOrder = 0;
}
