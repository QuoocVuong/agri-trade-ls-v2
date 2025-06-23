package com.yourcompany.agritrade.usermanagement.dto.response;

import lombok.Data;

@Data
public class UserInfoSimpleResponse {
  private Long id;
  private String fullName;
  private String avatarUrl;
  private boolean Online;
}
