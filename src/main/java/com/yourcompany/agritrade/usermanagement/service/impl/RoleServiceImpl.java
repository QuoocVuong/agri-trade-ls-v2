package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.usermanagement.domain.Permission;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.dto.request.RoleUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.RoleResponse;
import com.yourcompany.agritrade.usermanagement.mapper.RoleMapper;
import com.yourcompany.agritrade.usermanagement.repository.PermissionRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toRoleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Integer id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        return roleMapper.toRoleResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRolePermissions(Integer id, RoleUpdateRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        // Tìm các đối tượng Permission từ danh sách tên trong request
        Set<Permission> newPermissions = permissionRepository.findByNameIn(request.getPermissionNames());

        // Kiểm tra xem tất cả tên permission trong request có hợp lệ không
        if (newPermissions.size() != request.getPermissionNames().size()) {
            Set<String> foundNames = newPermissions.stream().map(Permission::getName).collect(Collectors.toSet());
            request.getPermissionNames().removeAll(foundNames); // Tìm ra tên không hợp lệ

            Set<String> invalidNames = new HashSet<>(request.getPermissionNames()); // Tạo bản sao để không sửa request gốc
            invalidNames.removeAll(foundNames); // Giữ lại những tên không tìm thấy
            throw new ResourceNotFoundException("Permission", "names", invalidNames.toString()); // Gọi constructor 3 tham số
        }

        // Cập nhật danh sách permissions cho role
        role.setPermissions(newPermissions);
        Role updatedRole = roleRepository.save(role);

        return roleMapper.toRoleResponse(updatedRole);
    }
}