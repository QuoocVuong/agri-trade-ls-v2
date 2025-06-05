package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.usermanagement.dto.request.RoleUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.RoleResponse;
import java.util.List;

public interface RoleService {
  List<RoleResponse> getAllRoles();

  RoleResponse getRoleById(Integer id);

  RoleResponse updateRolePermissions(Integer id, RoleUpdateRequest request);

}
