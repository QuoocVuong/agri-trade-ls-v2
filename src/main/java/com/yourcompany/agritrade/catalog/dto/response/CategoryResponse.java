package com.yourcompany.agritrade.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // Không hiện children nếu null empty
public class CategoryResponse {
  private Integer id;
  private String name;
  private String slug;
  private String description;
  private String imageUrl;
  private String blobPath;
  private Integer parentId;
  private Set<CategoryResponse> children; // Cho cấu trúc cây
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
