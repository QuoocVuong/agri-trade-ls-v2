package com.yourcompany.agritrade.interaction.dto.response;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class FollowUserResponse {
  private Long userId; // ID của user được follow/đang follow
  private String fullName;
  private String avatarUrl;
  private String farmName;
  private LocalDateTime followedAt; // Thời gian bắt đầu follow
}
