package com.yourcompany.agritradels.catalog.dto.response;
import lombok.Data;
@Data
public class ProductInfoResponse {
    private Long id;
    private String name;
    private String slug;
    private String thumbnailUrl; // Ảnh đại diện
}