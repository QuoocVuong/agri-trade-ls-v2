package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.PermissionRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.PermissionResponse;
import com.yourcompany.agritrade.usermanagement.service.PermissionService; // Import service
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions") // <<< Đường dẫn khớp với Frontend gọi
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Yêu cầu quyền Admin
public class AdminPermissionController {

    private final PermissionService permissionService; // Inject service

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    // Endpoint lấy chi tiết một permission (nếu cần)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable Integer id) {
        PermissionResponse permission = permissionService.getPermissionById(id);
        return ResponseEntity.ok(ApiResponse.success(permission));
    }


    // --- THÊM CÁC ENDPOINT CRUD ---

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE') or hasRole('ADMIN')") // Ví dụ dùng permission
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody PermissionRequest request) {
        PermissionResponse createdPermission = permissionService.createPermission(request);
        // Trả về 201 Created
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(createdPermission, "Permission created successfully."));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable Integer id,
            @Valid @RequestBody PermissionRequest request) {
        PermissionResponse updatedPermission = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedPermission, "Permission updated successfully."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Integer id) {
        permissionService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.success("Permission deleted successfully."));
    }
    // -----------------------------
}