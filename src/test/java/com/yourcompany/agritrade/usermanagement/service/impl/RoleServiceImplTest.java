package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.Permission;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.dto.request.RoleUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.RoleResponse;
import com.yourcompany.agritrade.usermanagement.mapper.RoleMapper;
import com.yourcompany.agritrade.usermanagement.repository.PermissionRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private RoleMapper roleMapper;

  @InjectMocks private RoleServiceImpl roleService;

  private Role adminRoleEntity, farmerRoleEntity;
  private RoleResponse adminRoleResponse, farmerRoleResponse;
  private Permission permReadUser, permWriteProduct;

  @BeforeEach
  void setUp() {
    permReadUser = new Permission("USER_READ_ALL", "Read all users");
    permReadUser.setId(1);
    permWriteProduct = new Permission("PRODUCT_WRITE_OWN", "Write own products");
    permWriteProduct.setId(2);

    adminRoleEntity = new Role(RoleType.ROLE_ADMIN);
    adminRoleEntity.setId(1);
    adminRoleEntity.setPermissions(new HashSet<>(Set.of(permReadUser)));

    farmerRoleEntity = new Role(RoleType.ROLE_FARMER);
    farmerRoleEntity.setId(2);
    farmerRoleEntity.setPermissions(new HashSet<>(Set.of(permWriteProduct)));

    adminRoleResponse = new RoleResponse();
    adminRoleResponse.setId(1);
    adminRoleResponse.setName(RoleType.ROLE_ADMIN);
    adminRoleResponse.setPermissionNames(Set.of("USER_READ_ALL"));

    farmerRoleResponse = new RoleResponse();
    farmerRoleResponse.setId(2);
    farmerRoleResponse.setName(RoleType.ROLE_FARMER);
    farmerRoleResponse.setPermissionNames(Set.of("PRODUCT_WRITE_OWN"));
  }

  @Nested
  @DisplayName("Get Role Tests")
  class GetRoleTests {
    @Test
    @DisplayName("Get All Roles - Success")
    void getAllRoles_shouldReturnListOfRoleResponses() {
      when(roleRepository.findAll()).thenReturn(List.of(adminRoleEntity, farmerRoleEntity));
      when(roleMapper.toRoleResponse(adminRoleEntity)).thenReturn(adminRoleResponse);
      when(roleMapper.toRoleResponse(farmerRoleEntity)).thenReturn(farmerRoleResponse);

      List<RoleResponse> result = roleService.getAllRoles();

      assertNotNull(result);
      assertEquals(2, result.size());
      assertTrue(result.contains(adminRoleResponse));
      assertTrue(result.contains(farmerRoleResponse));
      verify(roleRepository).findAll();
      verify(roleMapper, times(2)).toRoleResponse(any(Role.class));
    }

    @Test
    @DisplayName("Get All Roles - Empty List")
    void getAllRoles_whenNoRoles_shouldReturnEmptyList() {
      when(roleRepository.findAll()).thenReturn(Collections.emptyList());

      List<RoleResponse> result = roleService.getAllRoles();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Get Role By Id - Found")
    void getRoleById_whenRoleExists_shouldReturnRoleResponse() {
      when(roleRepository.findById(1)).thenReturn(Optional.of(adminRoleEntity));
      when(roleMapper.toRoleResponse(adminRoleEntity)).thenReturn(adminRoleResponse);

      RoleResponse result = roleService.getRoleById(1);

      assertNotNull(result);
      assertEquals(adminRoleResponse.getName(), result.getName());
      verify(roleRepository).findById(1);
    }

    @Test
    @DisplayName("Get Role By Id - Not Found - Throws ResourceNotFoundException")
    void getRoleById_whenRoleNotExists_shouldThrowResourceNotFoundException() {
      when(roleRepository.findById(99)).thenReturn(Optional.empty());

      assertThrows(ResourceNotFoundException.class, () -> roleService.getRoleById(99));
    }
  }

  @Nested
  @DisplayName("Update Role Permissions Tests")
  class UpdateRolePermissionsTests {
    @Test
    @DisplayName("Update Role Permissions - Success")
    void updateRolePermissions_success() {
      Integer roleId = adminRoleEntity.getId();
      Set<String> newPermissionNames = Set.of("USER_READ_ALL", "PRODUCT_WRITE_OWN");
      RoleUpdateRequest request = new RoleUpdateRequest();
      request.setPermissionNames(newPermissionNames);

      Set<Permission> newPermissionsSet = Set.of(permReadUser, permWriteProduct);

      Role updatedRoleEntity = new Role(RoleType.ROLE_ADMIN); // Tạo instance mới để mock save
      updatedRoleEntity.setId(roleId);
      updatedRoleEntity.setPermissions(newPermissionsSet);

      RoleResponse expectedResponse = new RoleResponse();
      expectedResponse.setId(roleId);
      expectedResponse.setName(RoleType.ROLE_ADMIN);
      expectedResponse.setPermissionNames(newPermissionNames);

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRoleEntity));
      when(permissionRepository.findByNameIn(newPermissionNames)).thenReturn(newPermissionsSet);
      when(roleRepository.save(any(Role.class))).thenReturn(updatedRoleEntity);
      when(roleMapper.toRoleResponse(updatedRoleEntity)).thenReturn(expectedResponse);

      RoleResponse result = roleService.updateRolePermissions(roleId, request);

      assertNotNull(result);
      assertEquals(expectedResponse.getPermissionNames(), result.getPermissionNames());

      ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
      verify(roleRepository).save(roleCaptor.capture());
      Role savedRole = roleCaptor.getValue();
      assertEquals(newPermissionsSet.size(), savedRole.getPermissions().size());
      assertTrue(savedRole.getPermissions().containsAll(newPermissionsSet));
    }

    @Test
    @DisplayName("Update Role Permissions - Role Not Found - Throws ResourceNotFoundException")
    void updateRolePermissions_whenRoleNotFound_shouldThrowResourceNotFoundException() {
      Integer roleId = 99;
      RoleUpdateRequest request = new RoleUpdateRequest();
      request.setPermissionNames(Set.of("USER_READ_ALL"));

      when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> roleService.updateRolePermissions(roleId, request));
      verify(permissionRepository, never()).findByNameIn(anySet());
      verify(roleRepository, never()).save(any(Role.class));
    }

    //        @Test
    //        @DisplayName("Update Role Permissions - Some Permissions Not Found - Throws
    // ResourceNotFoundException")
    //        void
    // updateRolePermissions_whenSomePermissionsNotFound_shouldThrowResourceNotFoundException() {
    //            Integer roleId = adminRoleEntity.getId();
    //            Set<String> requestedPermissionNames = Set.of("USER_READ_ALL",
    // "INVALID_PERMISSION");
    //            RoleUpdateRequest request = new RoleUpdateRequest();
    //            request.setPermissionNames(requestedPermissionNames);
    //
    //            // Giả sử "USER_READ_ALL" tồn tại, nhưng "INVALID_PERMISSION" không
    //            Set<Permission> foundPermissions = Set.of(permReadUser);
    //
    //            when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRoleEntity));
    //
    // when(permissionRepository.findByNameIn(requestedPermissionNames)).thenReturn(foundPermissions);
    //
    //            ResourceNotFoundException exception =
    // assertThrows(ResourceNotFoundException.class,
    //                    () -> roleService.updateRolePermissions(roleId, request));
    //
    //            assertTrue(exception.getMessage().contains("Permission not found with names :
    // '[INVALID_PERMISSION]'"));
    //            verify(roleRepository, never()).save(any(Role.class));
    //        }

    @Test
    @DisplayName("Update Role Permissions - Empty Permission List in Request - Clears Permissions")
    void updateRolePermissions_withEmptyPermissionList_shouldClearPermissions() {
      Integer roleId = adminRoleEntity.getId(); // adminRoleEntity ban đầu có permReadUser
      RoleUpdateRequest request = new RoleUpdateRequest();
      request.setPermissionNames(Collections.emptySet()); // Yêu cầu xóa hết permission

      Role updatedRoleEntity = new Role(RoleType.ROLE_ADMIN);
      updatedRoleEntity.setId(roleId);
      updatedRoleEntity.setPermissions(Collections.emptySet()); // Permissions rỗng sau khi cập nhật

      RoleResponse expectedResponse = new RoleResponse();
      expectedResponse.setId(roleId);
      expectedResponse.setName(RoleType.ROLE_ADMIN);
      expectedResponse.setPermissionNames(Collections.emptySet());

      when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRoleEntity));
      when(permissionRepository.findByNameIn(Collections.emptySet()))
          .thenReturn(Collections.emptySet());
      when(roleRepository.save(any(Role.class))).thenReturn(updatedRoleEntity);
      when(roleMapper.toRoleResponse(updatedRoleEntity)).thenReturn(expectedResponse);

      RoleResponse result = roleService.updateRolePermissions(roleId, request);

      assertNotNull(result);
      assertTrue(result.getPermissionNames().isEmpty());

      ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
      verify(roleRepository).save(roleCaptor.capture());
      assertTrue(roleCaptor.getValue().getPermissions().isEmpty());
    }
  }
}
