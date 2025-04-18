package com.yourcompany.agritradels.catalog.service.impl;

import com.github.slugify.Slugify; // Thêm thư viện slugify vào pom.xml
import com.yourcompany.agritradels.catalog.domain.Category;
import com.yourcompany.agritradels.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritradels.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritradels.catalog.mapper.CategoryMapper;
import com.yourcompany.agritradels.catalog.repository.CategoryRepository;
import com.yourcompany.agritradels.catalog.repository.ProductRepository; // Inject ProductRepository
import com.yourcompany.agritradels.catalog.service.CategoryService;
import com.yourcompany.agritradels.common.exception.BadRequestException;
import com.yourcompany.agritradels.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // Import StringUtils

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository; // Để kiểm tra khi xóa
    private final CategoryMapper categoryMapper;
    private final Slugify slugify = Slugify.builder().build(); // Khởi tạo slugify

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {
        // Lấy các category gốc và map đệ quy (cách đơn giản, có thể tối ưu nếu cây sâu)
        List<Category> rootCategories = categoryRepository.findByParentIsNull();
        return rootCategories.stream()
                .map(categoryMapper::toCategoryResponse) // MapStruct sẽ tự xử lý children nếu cấu hình đúng
                .collect(Collectors.toList());
        // Lưu ý: FetchType.EAGER cho children trong Category Entity có thể gây N+1 query.
        // Cân nhắc dùng JOIN FETCH hoặc EntityGraph nếu hiệu năng là vấn đề.
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return categoryMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        // Tạo slug nếu rỗng
        String slug = StringUtils.hasText(request.getSlug()) ? slugify.slugify(request.getSlug()) : slugify.slugify(request.getName());
        if (categoryRepository.existsBySlug(slug)) {
            throw new BadRequestException("Slug already exists: " + slug);
        }

        Category category = categoryMapper.requestToCategory(request);
        category.setSlug(slug); // Set slug đã tạo

        // Xử lý parent category
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", request.getParentId()));
            category.setParent(parent);
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created with id: {}", savedCategory.getId());
        return categoryMapper.toCategoryResponse(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Integer id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Cập nhật slug nếu name hoặc slug trong request thay đổi và khác slug hiện tại
        String newSlug = category.getSlug(); // Giữ slug cũ mặc định
        if (StringUtils.hasText(request.getSlug()) && !slugify.slugify(request.getSlug()).equals(category.getSlug())) {
            newSlug = slugify.slugify(request.getSlug());
        } else if (StringUtils.hasText(request.getName()) && !slugify.slugify(request.getName()).equals(category.getSlug()) && !StringUtils.hasText(request.getSlug())) {
            // Nếu chỉ đổi name mà không đổi slug, tự tạo slug mới từ name
            newSlug = slugify.slugify(request.getName());
        }

        // Kiểm tra trùng slug mới (với category khác)
        if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlugAndIdNot(newSlug, id)) {
            throw new BadRequestException("Slug already exists: " + newSlug);
        }

        // Cập nhật các trường từ request DTO
        categoryMapper.updateCategoryFromRequest(request, category);
        category.setSlug(newSlug); // Set slug mới

        // Cập nhật parent category
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) { // Không cho phép tự làm cha của chính mình
                throw new BadRequestException("Category cannot be its own parent.");
            }
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", request.getParentId()));
            category.setParent(parent);
        } else {
            category.setParent(null); // Bỏ cha nếu parentId là null
        }

        Category updatedCategory = categoryRepository.save(category);
        log.info("Category updated with id: {}", updatedCategory.getId());
        return categoryMapper.toCategoryResponse(updatedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Kiểm tra xem có sản phẩm nào thuộc danh mục này không
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new BadRequestException("Cannot delete category with id " + id + " because it contains " + productCount + " products.");
        }

        // Kiểm tra xem có danh mục con không (nếu logic yêu cầu không cho xóa khi có con)
        if (!category.getChildren().isEmpty()) {
            throw new BadRequestException("Cannot delete category with id " + id + " because it has subcategories.");
            // Hoặc xử lý chuyển các danh mục con lên cấp cha / xóa đệ quy tùy yêu cầu
        }


        categoryRepository.delete(category);
        log.info("Category deleted with id: {}", id);
    }
}