package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.dto.request.ProductImageRequest; // Import request DTO
import com.yourcompany.agritrade.catalog.dto.response.ProductImageResponse;
import com.yourcompany.agritrade.common.service.FileStorageService;
import org.mapstruct.*; // Import các annotation cần thiết
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ProductImageMapper { // Đổi thành abstract class

    @Autowired // Inject FileStorageService
    protected FileStorageService fileStorageService;

    // Map sang DTO Response

    @Mapping(target = "imageUrl", ignore = true) // Sẽ map trong @AfterMapping
    public abstract ProductImageResponse toProductImageResponse(ProductImage image);

    public abstract List<ProductImageResponse> toProductImageResponseList(List<ProductImage> images); // Hoặc Set

    @AfterMapping
    protected void afterToProductImageResponse(ProductImage image, @MappingTarget ProductImageResponse response) {
        if (image != null && image.getBlobPath() != null) {
            response.setImageUrl(fileStorageService.getFileUrl(image.getBlobPath())); // Tạo Signed URL
        }
    }


    // Map từ request DTO sang Entity (bỏ qua các trường không cần thiết khi tạo)
    @Mapping(target = "id", ignore = true) // ID sẽ tự tạo
    @Mapping(target = "product", ignore = true) // Product sẽ được set trong service
    @Mapping(target = "createdAt", ignore = true)
    // imageUrl và blobPath sẽ được set từ request
    public abstract ProductImage requestToProductImage(ProductImageRequest request);

    // Cập nhật Entity từ Request DTO (chỉ cập nhật isDefault và displayOrder)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "imageUrl", ignore = true) // Không cho cập nhật URL qua đây
    @Mapping(target = "blobPath", ignore = true) // Không cho cập nhật blobPath trực tiếp qua đây
    @Mapping(target = "createdAt", ignore = true)
    public abstract void updateProductImageFromRequest(ProductImageRequest request, @MappingTarget ProductImage image);
}