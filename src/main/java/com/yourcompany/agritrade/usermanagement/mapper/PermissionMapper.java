package com.yourcompany.agritrade.usermanagement.mapper;

import com.yourcompany.agritrade.usermanagement.domain.Permission;
import com.yourcompany.agritrade.usermanagement.dto.response.PermissionResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

  PermissionResponse toPermissionResponse(Permission permission);

  List<PermissionResponse> toPermissionResponseList(List<Permission> permissions);
}
