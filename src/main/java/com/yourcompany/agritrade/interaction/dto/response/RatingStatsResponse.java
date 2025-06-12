package com.yourcompany.agritrade.interaction.dto.response;

import lombok.Data;

@Data
public class RatingStatsResponse {
  private final Double averageRating;
  private final Long ratingCount;

  public RatingStatsResponse(Double averageRating, Long ratingCount) {
    this.averageRating = averageRating;
    this.ratingCount = ratingCount != null ? ratingCount : 0L; // Đảm bảo count không null
  }
}
