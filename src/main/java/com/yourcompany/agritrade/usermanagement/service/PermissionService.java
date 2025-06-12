// src/main/java/com/yourcompany/agritrade/usermanagement/service/PermissionService.java
package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.usermanagement.dto.request.PermissionRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.PermissionResponse;
import java.util.List;

public interface PermissionService {
  List<PermissionResponse> getAllPermissions();

  // Thêm các phương thức mới
  PermissionResponse createPermission(PermissionRequest request);

  PermissionResponse updatePermission(Integer id, PermissionRequest request);

  void deletePermission(Integer id);

  PermissionResponse getPermissionById(Integer id);
}
