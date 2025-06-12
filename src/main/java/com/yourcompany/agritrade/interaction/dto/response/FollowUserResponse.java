package com.yourcompany.agritrade.interaction.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FollowUserResponse {
  private Long userId; // ID của user được follow/đang follow
  private String fullName;
  private String avatarUrl;
  private String farmName;
  private LocalDateTime followedAt; // Thời gian bắt đầu follow
}
