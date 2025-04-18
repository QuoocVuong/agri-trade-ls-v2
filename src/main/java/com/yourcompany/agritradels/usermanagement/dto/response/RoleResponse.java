package com.yourcompany.agritradels.usermanagement.dto.response;

import com.yourcompany.agritradels.common.model.RoleType;
import lombok.Data;
import java.util.Set;

@Data
public class RoleResponse {
    private Integer id;
    private RoleType name;
    private Set<String> permissionNames; // Hiển thị tên các permission
}