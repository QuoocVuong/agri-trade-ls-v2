package com.yourcompany.agritradels.catalog.service;

import com.yourcompany.agritradels.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritradels.catalog.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {
    List<CategoryResponse> getCategoryTree(); // Lấy toàn bộ cây danh mục
    CategoryResponse getCategoryBySlug(String slug);
    CategoryResponse getCategoryById(Integer id);
    CategoryResponse createCategory(CategoryRequest request); // Admin only
    CategoryResponse updateCategory(Integer id, CategoryRequest request); // Admin only
    void deleteCategory(Integer id); // Admin only (cần kiểm tra có sản phẩm không)
}