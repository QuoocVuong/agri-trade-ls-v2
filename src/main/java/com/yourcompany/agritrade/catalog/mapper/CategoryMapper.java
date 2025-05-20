package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.common.service.FileStorageService;
import java.util.List;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class CategoryMapper {

  @Autowired // Inject FileStorageService
  protected FileStorageService fileStorageService;

  // --- Response Mappers ---
  @Mapping(target = "parentId", source = "parent.id")
  // MapStruct sẽ tự gọi lại mapper này cho children nếu kiểu giống nhau
  @Mapping(target = "imageUrl", ignore = true) // Ignore để tạo động
  @Mapping(target = "blobPath", source = "blobPath") // Map blobPath
  public abstract CategoryResponse toCategoryResponse(Category category);

  @AfterMapping // Tạo imageUrl động sau khi map
  protected void populateImageUrl(Category category, @MappingTarget CategoryResponse response) {
    if (category != null && category.getBlobPath() != null) {
      try {
        response.setImageUrl(fileStorageService.getFileUrl(category.getBlobPath()));
      } catch (Exception e) {
        // log.error(...)
        response.setImageUrl("assets/images/placeholder-category.png"); // Placeholder
      }
    } else {
      response.setImageUrl("assets/images/placeholder-category.png"); // Placeholder
    }
  }

  public abstract CategoryInfoResponse toCategoryInfoResponse(Category category);

  public abstract List<CategoryResponse> toCategoryResponseList(
      List<Category> categories); // Map List

  // --- Request Mapper ---
  // Bỏ qua id, parent, children, timestamps khi map từ request
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "parent", ignore = true) // parent sẽ được set trong service
  @Mapping(target = "children", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "imageUrl", ignore = true) // Ignore imageUrl từ request khi map vào entity
  // blobPath sẽ được map từ request
  public abstract Category requestToCategory(CategoryRequest request);

  // --- Update Mapper ---
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "parent", ignore = true)
  @Mapping(target = "children", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "imageUrl", ignore = true) // Không cập nhật imageUrl qua đây
  @Mapping(target = "blobPath", ignore = true) // Không cập nhật blobPath qua đây (xử lý riêng)
  public abstract void updateCategoryFromRequest(
      CategoryRequest request, @MappingTarget Category category);
}
