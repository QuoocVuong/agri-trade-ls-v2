package com.yourcompany.agritrade.catalog.service;

import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {
  List<CategoryResponse> getCategoryTree(); // Lấy toàn bộ cây danh mục

  CategoryResponse getCategoryBySlug(String slug);

  CategoryResponse getCategoryById(Integer id);

  CategoryResponse createCategory(CategoryRequest request); // Admin only

  CategoryResponse updateCategory(Integer id, CategoryRequest request); // Admin only

  void deleteCategory(Integer id); // Admin only (cần kiểm tra có sản phẩm không)

  /** Lấy danh sách phẳng tất cả các danh mục */
  List<CategoryResponse> getAllCategoriesForDropdown();
}
