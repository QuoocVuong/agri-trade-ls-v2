package com.yourcompany.agritrade.usermanagement.dto.response;

import com.yourcompany.agritrade.common.model.RoleType;
import java.util.Set;
import lombok.Data;

@Data
public class RoleResponse {
  private Integer id;
  private RoleType name;
  private Set<String> permissionNames; // Hiển thị tên các permission
}
