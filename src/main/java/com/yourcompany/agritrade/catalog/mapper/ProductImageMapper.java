package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.dto.request.ProductImageRequest; // Import request DTO
import com.yourcompany.agritrade.catalog.dto.response.ProductImageResponse;
import org.mapstruct.*; // Import các annotation cần thiết
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {

    ProductImageResponse toProductImageResponse(ProductImage image);

    List<ProductImageResponse> toProductImageResponseList(List<ProductImage> images);

    // Map từ request DTO sang Entity (bỏ qua các trường không cần thiết khi tạo)
    @Mapping(target = "id", ignore = true) // ID sẽ tự tạo
    @Mapping(target = "product", ignore = true) // Product sẽ được set trong service
    @Mapping(target = "createdAt", ignore = true)
    ProductImage requestToProductImage(ProductImageRequest request);

    // Cập nhật Entity từ Request DTO (chỉ cập nhật isDefault và displayOrder)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "imageUrl", ignore = true) // Không cho cập nhật URL qua đây
    @Mapping(target = "createdAt", ignore = true)
    void updateProductImageFromRequest(ProductImageRequest request, @MappingTarget ProductImage image);
}