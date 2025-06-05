package com.yourcompany.agritrade.usermanagement.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import lombok.Data;

@Data
public class RoleUpdateRequest {

  @NotEmpty(message = "Permission names cannot be empty")
  private Set<String> permissionNames; // Danh sách tên các permission muốn gán cho Role
}
