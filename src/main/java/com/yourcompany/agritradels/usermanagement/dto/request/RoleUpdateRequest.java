package com.yourcompany.agritradels.usermanagement.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.Set;

@Data
public class RoleUpdateRequest {
    // Có thể cho phép cập nhật các thuộc tính khác của Role nếu có
    @NotEmpty(message = "Permission names cannot be empty")
    private Set<String> permissionNames; // Danh sách tên các permission muốn gán cho Role
}