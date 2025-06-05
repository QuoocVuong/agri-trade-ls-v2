package com.yourcompany.agritrade.catalog.service.impl;

import com.github.slugify.Slugify;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.mapper.CategoryMapper;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository; // Để kiểm tra khi xóa
  private final CategoryMapper categoryMapper;
  private final Slugify slugify = Slugify.builder().build(); // Khởi tạo slugify
  private final FileStorageService fileStorageService;

  @Override
  @Transactional(readOnly = true)
  public List<CategoryResponse> getAllCategoriesForDropdown() {
    log.debug("Fetching all categories for dropdown");
    List<Category> categories =
        categoryRepository.findAll(
            Sort.by(Sort.Direction.ASC, "name")); // Lấy tất cả, sắp xếp theo tên


    return categoryMapper.toCategoryResponseList(categories); // Sử dụng MapStruct mapper
  }

  @Override
  @Transactional(readOnly = true)
  public List<CategoryResponse> getCategoryTree() {
    // Lấy các category gốc và map đệ quy
    List<Category> rootCategories = categoryRepository.findByParentIsNull();
    return rootCategories.stream()
        .map(categoryMapper::toCategoryResponse) // MapStruct sẽ tự xử lý children nếu cấu hình đúng
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public CategoryResponse getCategoryBySlug(String slug) {
    Category category =
        categoryRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
    return categoryMapper.toCategoryResponse(category);
  }

  @Override
  @Transactional(readOnly = true)
  public CategoryResponse getCategoryById(Integer id) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    return categoryMapper.toCategoryResponse(category);
  }

  @Override
  @Transactional
  public CategoryResponse createCategory(CategoryRequest request) {
    // Tạo slug nếu rỗng
    String slug =
        StringUtils.hasText(request.getSlug())
            ? slugify.slugify(request.getSlug())
            : slugify.slugify(request.getName());
    if (categoryRepository.existsBySlug(slug)) {
      throw new BadRequestException("Slug already exists: " + slug);
    }

    Category category = categoryMapper.requestToCategory(request);
    category.setSlug(slug); // Set slug đã tạo
    category.setBlobPath(request.getBlobPath());

    // Xử lý parent category
    if (request.getParentId() != null) {
      Category parent =
          categoryRepository
              .findById(request.getParentId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Parent Category", "id", request.getParentId()));
      category.setParent(parent);
    }

    Category savedCategory = categoryRepository.save(category);
    log.info("Category created with id: {}", savedCategory.getId());
    return categoryMapper.toCategoryResponse(savedCategory);
  }

  @Override
  @Transactional
  public CategoryResponse updateCategory(Integer id, CategoryRequest request) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

    String oldBlobPath = category.getBlobPath(); // Lưu lại blobPath cũ

    // Cập nhật slug nếu name hoặc slug trong request thay đổi và khác slug hiện tại
    String newSlug = category.getSlug(); // Giữ slug cũ mặc định
    if (StringUtils.hasText(request.getSlug())
        && !slugify.slugify(request.getSlug()).equals(category.getSlug())) {
      newSlug = slugify.slugify(request.getSlug());
    } else if (StringUtils.hasText(request.getName())
        && !slugify.slugify(request.getName()).equals(category.getSlug())
        && !StringUtils.hasText(request.getSlug())) {
      // Nếu chỉ đổi name mà không đổi slug, tự tạo slug mới từ name
      newSlug = slugify.slugify(request.getName());
    }

    // Kiểm tra trùng slug mới (với category khác)
    if (!newSlug.equals(category.getSlug())
        && categoryRepository.existsBySlugAndIdNot(newSlug, id)) {
      throw new BadRequestException("Slug already exists: " + newSlug);
    }

    // Cập nhật các trường từ request DTO
    categoryMapper.updateCategoryFromRequest(request, category);
    category.setSlug(newSlug); // Set slug mới

    // Cập nhật blobPath nếu có trong request
    // Nếu blobPath trong request khác blobPath cũ -> ảnh đã thay đổi
    boolean imageChanged =
        request.getBlobPath() != null && !request.getBlobPath().equals(oldBlobPath);
    // Nếu blobPath trong request là null nhưng trước đó có ảnh -> ảnh đã bị xóa
    boolean imageRemoved = request.getBlobPath() == null && oldBlobPath != null;

    if (imageChanged || imageRemoved) {
      category.setBlobPath(request.getBlobPath()); // Cập nhật hoặc xóa blobPath
    }

    // Cập nhật parent category
    if (request.getParentId() != null) {
      if (request.getParentId().equals(id)) { // Không cho phép tự làm cha của chính mình
        throw new BadRequestException("Category cannot be its own parent.");
      }
      Category parent =
          categoryRepository
              .findById(request.getParentId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Parent Category", "id", request.getParentId()));
      category.setParent(parent);
    } else {
      category.setParent(null); // Bỏ cha nếu parentId là null
    }

    Category updatedCategory = categoryRepository.save(category);

    //  xóa ảnh cũ trên storage SAU KHI đã lưu thành công
    if ((imageChanged || imageRemoved) && StringUtils.hasText(oldBlobPath)) {
      try {
        log.info("Deleting old category image: {}", oldBlobPath);
        fileStorageService.delete(oldBlobPath);
      } catch (Exception e) {
        log.error("Failed to delete old category image file from storage: {}", oldBlobPath, e);

      }
    }

    log.info("Category updated with id: {}", updatedCategory.getId());
    // Map lại để có imageUrl động
    return categoryMapper.toCategoryResponse(updatedCategory);
  }

  @Override
  @Transactional
  public void deleteCategory(Integer id) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

    // Kiểm tra xem có sản phẩm nào thuộc danh mục này không
    long productCount = productRepository.countByCategoryId(id);
    if (productCount > 0) {
      throw new BadRequestException(
          "Cannot delete category with id "
              + id
              + " because it contains "
              + productCount
              + " products.");
    }

    // Kiểm tra xem có danh mục con không
    if (!category.getChildren().isEmpty()) {
      throw new BadRequestException(
          "Cannot delete category with id " + id + " because it has subcategories.");

    }

    String blobPathToDelete = category.getBlobPath(); // Lấy blobPath trước khi xóa entity

    categoryRepository.delete(category);
    log.info("Category deleted with id: {}", id);

    // Xóa ảnh trên storage nếu có
    if (StringUtils.hasText(blobPathToDelete)) {
      try {
        log.info("Deleting category image after entity deletion: {}", blobPathToDelete);
        fileStorageService.delete(blobPathToDelete);
      } catch (Exception e) {
        log.error(
            "Failed to delete category image file from storage after entity deletion: {}",
            blobPathToDelete,
            e);
      }
    }
  }
}
