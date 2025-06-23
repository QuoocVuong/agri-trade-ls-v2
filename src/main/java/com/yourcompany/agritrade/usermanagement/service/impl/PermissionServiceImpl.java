package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.usermanagement.domain.Permission;
import com.yourcompany.agritrade.usermanagement.dto.request.PermissionRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.PermissionResponse;
import com.yourcompany.agritrade.usermanagement.mapper.PermissionMapper;
import com.yourcompany.agritrade.usermanagement.repository.PermissionRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.service.PermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

  private final PermissionRepository permissionRepository;
  private final PermissionMapper permissionMapper;
  private final RoleRepository roleRepository; // Inject RoleRepository

  @Override
  @Transactional(readOnly = true)
  public List<PermissionResponse> getAllPermissions() {
    return permissionMapper.toPermissionResponseList(permissionRepository.findAll());
  }

  @Override
  @Transactional(readOnly = true)
  public PermissionResponse getPermissionById(Integer id) {
    Permission permission =
        permissionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    return permissionMapper.toPermissionResponse(permission);
  }

  @Override
  @Transactional
  public PermissionResponse createPermission(PermissionRequest request) {
    // Kiểm tra xem tên permission đã tồn tại chưa
    if (permissionRepository.existsByName(request.getName())) {
      throw new BadRequestException("Permission name '" + request.getName() + "' already exists.");
    }

    Permission newPermission = new Permission();
    newPermission.setName(request.getName());
    newPermission.setDescription(request.getDescription());

    Permission savedPermission = permissionRepository.save(newPermission);
    log.info("Created new permission: {}", savedPermission.getName());
    return permissionMapper.toPermissionResponse(savedPermission);
  }

  @Override
  @Transactional
  public PermissionResponse updatePermission(Integer id, PermissionRequest request) {
    Permission existingPermission =
        permissionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));

    // Kiểm tra trùng tên nếu tên bị thay đổi
    if (!existingPermission.getName().equals(request.getName())
        && permissionRepository.existsByName(request.getName())) {
      throw new BadRequestException("Permission name '" + request.getName() + "' already exists.");
    }

    existingPermission.setName(request.getName());
    existingPermission.setDescription(request.getDescription());

    Permission updatedPermission = permissionRepository.save(existingPermission);
    log.info("Updated permission with id {}: {}", id, updatedPermission.getName());
    return permissionMapper.toPermissionResponse(updatedPermission);
  }

  @Override
  @Transactional
  public void deletePermission(Integer id) {
    Permission permissionToDelete =
        permissionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));

    //  Lấy tất cả Role và kiểm tra
    boolean isInUse =
        roleRepository.findAll().stream()
            .anyMatch(role -> role.getPermissions().contains(permissionToDelete));

    if (isInUse) {
      log.warn(
          "Attempted to delete permission '{}' which is currently assigned to one or more roles.",
          permissionToDelete.getName());
      throw new BadRequestException(
          "Cannot delete permission '"
              + permissionToDelete.getName()
              + "' because it is currently assigned to one or more roles. Please remove it from all roles first.");
    }

    permissionRepository.delete(permissionToDelete);
    log.info("Deleted permission with id {}: {}", id, permissionToDelete.getName());
  }
}
