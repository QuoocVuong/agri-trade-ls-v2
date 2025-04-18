package com.yourcompany.agritradels.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // Không hiện children nếu null/empty
public class CategoryResponse {
    private Integer id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private Integer parentId;
    private Set<CategoryResponse> children; // Cho cấu trúc cây
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}