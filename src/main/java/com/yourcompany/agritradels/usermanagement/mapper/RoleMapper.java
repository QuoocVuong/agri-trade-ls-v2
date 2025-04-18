package com.yourcompany.agritradels.usermanagement.mapper;

import com.yourcompany.agritradels.usermanagement.domain.Permission;
import com.yourcompany.agritradels.usermanagement.domain.Role;
import com.yourcompany.agritradels.usermanagement.dto.response.RoleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(source = "permissions", target = "permissionNames", qualifiedByName = "permissionsToNames")
    RoleResponse toRoleResponse(Role role);

    @Named("permissionsToNames")
    default Set<String> permissionsToNames(Set<Permission> permissions) {
        if (permissions == null) {
            return null;
        }
        return permissions.stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }
}