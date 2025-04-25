package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import org.mapstruct.*;

import java.util.List; // Import List

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    // --- Response Mappers ---
    @Mapping(target = "parentId", source = "parent.id")
    // MapStruct sẽ tự gọi lại mapper này cho children nếu kiểu giống nhau
    CategoryResponse toCategoryResponse(Category category);

    CategoryInfoResponse toCategoryInfoResponse(Category category);

    List<CategoryResponse> toCategoryResponseList(List<Category> categories); // Map List

    // --- Request Mapper ---
    // Bỏ qua id, parent, children, timestamps khi map từ request
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true) // parent sẽ được set trong service
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Category requestToCategory(CategoryRequest request);

    // --- Update Mapper ---
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateCategoryFromRequest(CategoryRequest request, @MappingTarget Category category);
}