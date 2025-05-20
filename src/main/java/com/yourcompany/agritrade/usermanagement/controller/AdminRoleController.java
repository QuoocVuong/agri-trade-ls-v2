package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.RoleUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.RoleResponse;
import com.yourcompany.agritrade.usermanagement.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles") // Đặt dưới /api/admin
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Chỉ Admin được truy cập
public class AdminRoleController {

  private final RoleService roleService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
    List<RoleResponse> roles = roleService.getAllRoles();
    return ResponseEntity.ok(ApiResponse.success(roles));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Integer id) {
    RoleResponse role = roleService.getRoleById(id);
    return ResponseEntity.ok(ApiResponse.success(role));
  }

  @PutMapping("/{id}/permissions")
  public ResponseEntity<ApiResponse<RoleResponse>> updateRolePermissions(
      @PathVariable Integer id, @Valid @RequestBody RoleUpdateRequest request) {
    RoleResponse updatedRole = roleService.updateRolePermissions(id, request);
    return ResponseEntity.ok(
        ApiResponse.success(updatedRole, "Role permissions updated successfully"));
  }
}
