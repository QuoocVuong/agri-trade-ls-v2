// src/main/java/com/yourcompany/agritrade/usermanagement/service/PermissionService.java
package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.usermanagement.dto.request.PermissionRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.PermissionResponse;
import java.util.List;

public interface PermissionService {
    List<PermissionResponse> getAllPermissions();
    // Có thể thêm các phương thức khác nếu cần CRUD Permissions

    // Thêm các phương thức mới
    PermissionResponse createPermission(PermissionRequest request);
    PermissionResponse updatePermission(Integer id, PermissionRequest request);
    void deletePermission(Integer id);
    PermissionResponse getPermissionById(Integer id); // Thêm hàm lấy theo ID nếu cần
}