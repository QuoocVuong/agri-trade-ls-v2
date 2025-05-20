package com.yourcompany.agritrade.catalog.dto.response;

import lombok.Data;

// DTO đơn giản để nhúng vào Product
@Data
public class CategoryInfoResponse {
  private Integer id;
  private String name;
  private String slug;
}
