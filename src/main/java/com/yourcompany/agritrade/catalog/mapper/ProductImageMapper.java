package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.dto.request.ProductImageRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductImageResponse;
import com.yourcompany.agritrade.common.service.FileStorageService;
import java.util.List;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ProductImageMapper {

  @Autowired // Inject FileStorageService
  protected FileStorageService fileStorageService;

  // Map sang DTO Response
  @Mapping(target = "imageUrl", ignore = true) // Ignore vì sẽ tạo động
  public abstract ProductImageResponse toProductImageResponse(ProductImage image);

  public abstract List<ProductImageResponse> toProductImageResponseList(
      List<ProductImage> images);

  @AfterMapping
  protected void populateImageUrl(
      ProductImage image, @MappingTarget ProductImageResponse response) {
    if (image != null && image.getBlobPath() != null) {
      try {
        response.setImageUrl(fileStorageService.getFileUrl(image.getBlobPath()));
      } catch (Exception e) {

        response.setImageUrl("assets/images/placeholder-image.png");
      }
    } else {
      response.setImageUrl(
          "assets/images/placeholder-image.png");
    }
  }

  // Map từ request DTO sang Entity
  @Mapping(target = "id", ignore = true) // ID sẽ tự tạo
  @Mapping(target = "product", ignore = true) // Product sẽ được set trong service
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(
      target = "imageUrl",
      ignore = true) //  Không map imageUrl từ request vào entity
  // blobPath sẽ được map từ request
  public abstract ProductImage requestToProductImage(ProductImageRequest request);

  // Cập nhật Entity từ Request DTO
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "product", ignore = true)
  @Mapping(target = "imageUrl", ignore = true)
  @Mapping(target = "blobPath", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  public abstract void updateProductImageFromRequest(
      ProductImageRequest request, @MappingTarget ProductImage image);
}
