package com.yourcompany.agritrade.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionRequest {

    @NotBlank(message = "Permission name is required")
    @Size(max = 100, message = "Permission name must be less than 100 characters")
    // Có thể thêm pattern validation nếu muốn tên chỉ chứa ký tự/số/dấu gạch dưới
    // @Pattern(regexp = "^[A-Z0-9_]+$", message = "Permission name must be uppercase letters, numbers, and underscores only")
    private String name;

    @Size(max = 255, message = "Description must be less than 255 characters")
    private String description;
}