package com.yourcompany.agritradels.usermanagement.service;

import com.yourcompany.agritradels.usermanagement.dto.request.RoleUpdateRequest;
import com.yourcompany.agritradels.usermanagement.dto.response.RoleResponse;
import java.util.List;

public interface RoleService {
    List<RoleResponse> getAllRoles();
    RoleResponse getRoleById(Integer id);
    RoleResponse updateRolePermissions(Integer id, RoleUpdateRequest request);
    // Có thể thêm tạo/xóa Role nếu cần, nhưng thường Role là cố định
}