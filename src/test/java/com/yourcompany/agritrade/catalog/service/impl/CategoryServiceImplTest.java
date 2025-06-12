package com.yourcompany.agritrade.catalog.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.github.slugify.Slugify;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.mapper.CategoryMapper;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private ProductRepository productRepository;
  @Mock private CategoryMapper categoryMapper;
  @Mock private FileStorageService fileStorageService;

  @Spy private Slugify slugify = Slugify.builder().build(); // Sử dụng instance thật của Slugify

  @InjectMocks private CategoryServiceImpl categoryService;

  private Category category1, category2, parentCategory;
  private CategoryResponse categoryResponse1, categoryResponse2, parentCategoryResponse;
  private CategoryRequest categoryRequest;

  @BeforeEach
  void setUp() {
    parentCategory = new Category();
    parentCategory.setId(1);
    parentCategory.setName("Danh mục cha");
    parentCategory.setSlug("danh-muc-cha");

    category1 = new Category();
    category1.setId(10);
    category1.setName("Rau Ăn Lá");
    category1.setSlug("rau-an-la");
    category1.setBlobPath("images/rau-an-la.jpg");
    category1.setParent(parentCategory);
    parentCategory.setChildren(Set.of(category1)); // Giả lập parent có child

    category2 = new Category();
    category2.setId(20);
    category2.setName("Củ Quả");
    category2.setSlug("cu-qua");

    parentCategoryResponse = new CategoryResponse();
    parentCategoryResponse.setId(1);
    parentCategoryResponse.setName("Danh mục cha");

    categoryResponse1 = new CategoryResponse();
    categoryResponse1.setId(10);
    categoryResponse1.setName("Rau Ăn Lá");
    categoryResponse1.setSlug("rau-an-la");
    categoryResponse1.setImageUrl(
        "http://example.com/images/rau-an-la.jpg"); // Giả sử mapper đã xử lý
    categoryResponse1.setParentId(1);

    categoryResponse2 = new CategoryResponse();
    categoryResponse2.setId(20);
    categoryResponse2.setName("Củ Quả");
    categoryResponse2.setSlug("cu-qua");

    categoryRequest = new CategoryRequest();
    categoryRequest.setName("Trái Cây Mới");
    categoryRequest.setDescription("Mô tả trái cây mới");
    categoryRequest.setBlobPath("images/trai-cay-moi.jpg");
  }

  @Nested
  @DisplayName("Get Category Tests")
  class GetCategoryTests {
    @Test
    @DisplayName("Get All Categories For Dropdown - Success")
    void getAllCategoriesForDropdown_shouldReturnSortedCategories() {
      when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")))
          .thenReturn(List.of(category2, category1)); // Giả sử trả về không theo thứ tự tên
      when(categoryMapper.toCategoryResponseList(List.of(category2, category1)))
          .thenReturn(
              List.of(categoryResponse2, categoryResponse1)); // Mapper giữ nguyên thứ tự từ repo

      List<CategoryResponse> result = categoryService.getAllCategoriesForDropdown();

      assertNotNull(result);
      assertEquals(2, result.size());
      // Service không sắp xếp lại, nó dựa vào Sort của repository
      assertEquals(categoryResponse2.getName(), result.get(0).getName());
      assertEquals(categoryResponse1.getName(), result.get(1).getName());
      verify(categoryRepository).findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    @DisplayName("Get Category Tree - Success")
    void getCategoryTree_shouldReturnRootCategoriesWithChildren() {
      // Giả sử category2 là root, category1 là con của parentCategory (root khác)
      Category rootCategory2 = new Category();
      rootCategory2.setId(20);
      rootCategory2.setName("Củ Quả");
      CategoryResponse rootResponse2 = new CategoryResponse();
      rootResponse2.setId(20);
      rootResponse2.setName("Củ Quả");

      when(categoryRepository.findByParentIsNull())
          .thenReturn(List.of(parentCategory, rootCategory2));
      when(categoryMapper.toCategoryResponse(parentCategory))
          .thenReturn(parentCategoryResponse); // Giả sử mapper xử lý children
      when(categoryMapper.toCategoryResponse(rootCategory2)).thenReturn(rootResponse2);

      List<CategoryResponse> result = categoryService.getCategoryTree();

      assertNotNull(result);
      assertEquals(2, result.size());
      verify(categoryRepository).findByParentIsNull();
    }

    @Test
    @DisplayName("Get Category By Slug - Found")
    void getCategoryBySlug_whenFound_shouldReturnCategoryResponse() {
      when(categoryRepository.findBySlug("rau-an-la")).thenReturn(Optional.of(category1));
      when(categoryMapper.toCategoryResponse(category1)).thenReturn(categoryResponse1);

      CategoryResponse result = categoryService.getCategoryBySlug("rau-an-la");

      assertNotNull(result);
      assertEquals(categoryResponse1.getName(), result.getName());
    }

    @Test
    @DisplayName("Get Category By Slug - Not Found - Throws ResourceNotFoundException")
    void getCategoryBySlug_whenNotFound_shouldThrowResourceNotFoundException() {
      when(categoryRepository.findBySlug("slug-khong-ton-tai")).thenReturn(Optional.empty());
      assertThrows(
          ResourceNotFoundException.class,
          () -> categoryService.getCategoryBySlug("slug-khong-ton-tai"));
    }

    @Test
    @DisplayName("Get Category By Id - Found")
    void getCategoryById_whenFound_shouldReturnCategoryResponse() {
      when(categoryRepository.findById(10)).thenReturn(Optional.of(category1));
      when(categoryMapper.toCategoryResponse(category1)).thenReturn(categoryResponse1);

      CategoryResponse result = categoryService.getCategoryById(10);
      assertNotNull(result);
      assertEquals(categoryResponse1.getName(), result.getName());
    }

    @Test
    @DisplayName("Get Category By Id - Not Found - Throws ResourceNotFoundException")
    void getCategoryById_whenNotFound_shouldThrowResourceNotFoundException() {
      when(categoryRepository.findById(99)).thenReturn(Optional.empty());
      assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(99));
    }
  }

  @Nested
  @DisplayName("Create Category Tests")
  class CreateCategoryTests {
    @Test
    @DisplayName("Create Category - Success with Auto-Generated Slug and No Parent")
    void createCategory_successWithAutoSlugNoParent() {
      Category newCategoryEntity = new Category(); // Entity được mapper tạo ra
      newCategoryEntity.setName(categoryRequest.getName());
      newCategoryEntity.setBlobPath(categoryRequest.getBlobPath());
      // Slug sẽ được service gán

      Category savedCategoryEntity = new Category(); // Entity sau khi save
      savedCategoryEntity.setId(30);
      savedCategoryEntity.setName(categoryRequest.getName());
      savedCategoryEntity.setSlug(slugify.slugify(categoryRequest.getName())); // Slug đã gán
      savedCategoryEntity.setBlobPath(categoryRequest.getBlobPath());

      CategoryResponse expectedResponse = new CategoryResponse();
      expectedResponse.setId(30);
      expectedResponse.setName(categoryRequest.getName());
      expectedResponse.setSlug(savedCategoryEntity.getSlug());

      when(categoryRepository.existsBySlug(slugify.slugify(categoryRequest.getName())))
          .thenReturn(false);
      when(categoryMapper.requestToCategory(categoryRequest)).thenReturn(newCategoryEntity);
      when(categoryRepository.save(any(Category.class))).thenReturn(savedCategoryEntity);
      when(categoryMapper.toCategoryResponse(savedCategoryEntity)).thenReturn(expectedResponse);

      CategoryResponse result = categoryService.createCategory(categoryRequest);

      assertNotNull(result);
      assertEquals(expectedResponse.getName(), result.getName());
      assertEquals(slugify.slugify(categoryRequest.getName()), result.getSlug());

      ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
      verify(categoryRepository).save(categoryCaptor.capture());
      assertEquals(slugify.slugify(categoryRequest.getName()), categoryCaptor.getValue().getSlug());
      assertNull(categoryCaptor.getValue().getParent());
    }

    @Test
    @DisplayName("Create Category - Success with Provided Slug and Parent")
    void createCategory_successWithProvidedSlugAndParent() {
      categoryRequest.setSlug("trai-cay-moi-slug");
      categoryRequest.setParentId(parentCategory.getId());

      Category newCategoryEntity = new Category();
      newCategoryEntity.setName(categoryRequest.getName());
      newCategoryEntity.setBlobPath(categoryRequest.getBlobPath());

      Category savedCategoryEntity = new Category();
      savedCategoryEntity.setId(31);
      savedCategoryEntity.setName(categoryRequest.getName());
      savedCategoryEntity.setSlug("trai-cay-moi-slug");
      savedCategoryEntity.setParent(parentCategory);

      CategoryResponse expectedResponse = new CategoryResponse();
      expectedResponse.setId(31);
      expectedResponse.setSlug("trai-cay-moi-slug");
      expectedResponse.setParentId(parentCategory.getId());

      when(categoryRepository.existsBySlug("trai-cay-moi-slug")).thenReturn(false);
      when(categoryMapper.requestToCategory(categoryRequest)).thenReturn(newCategoryEntity);
      when(categoryRepository.findById(parentCategory.getId()))
          .thenReturn(Optional.of(parentCategory));
      when(categoryRepository.save(any(Category.class))).thenReturn(savedCategoryEntity);
      when(categoryMapper.toCategoryResponse(savedCategoryEntity)).thenReturn(expectedResponse);

      CategoryResponse result = categoryService.createCategory(categoryRequest);

      assertNotNull(result);
      assertEquals("trai-cay-moi-slug", result.getSlug());
      assertEquals(parentCategory.getId(), result.getParentId());
      verify(categoryRepository).save(argThat(cat -> cat.getParent().equals(parentCategory)));
    }

    @Test
    @DisplayName("Create Category - Slug Already Exists - Throws BadRequestException")
    void createCategory_whenSlugExists_shouldThrowBadRequestException() {
      categoryRequest.setSlug("rau-an-la"); // Slug đã tồn tại
      when(categoryRepository.existsBySlug("rau-an-la")).thenReturn(true);

      assertThrows(
          BadRequestException.class, () -> categoryService.createCategory(categoryRequest));
      verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Create Category - Parent Category Not Found - Throws ResourceNotFoundException")
    void createCategory_whenParentNotFound_shouldThrowResourceNotFoundException() {
      categoryRequest.setParentId(999); // Parent ID không tồn tại
      when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
      when(categoryMapper.requestToCategory(categoryRequest)).thenReturn(new Category());
      when(categoryRepository.findById(999)).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class, () -> categoryService.createCategory(categoryRequest));
    }
  }

  @Nested
  @DisplayName("Update Category Tests")
  class UpdateCategoryTests {
    @Test
    @DisplayName("Update Category - Success with Name, Slug, Parent, and Image Change")
    void updateCategory_successWithAllChanges() {
      Integer categoryIdToUpdate = category1.getId(); // 10
      categoryRequest.setName("Rau Sạch Mới");
      categoryRequest.setSlug("rau-sach-moi-slug");
      categoryRequest.setParentId(null); // Bỏ parent
      categoryRequest.setBlobPath("images/new-image.jpg"); // Ảnh mới

      Category categoryToUpdate = new Category(); // Tạo bản sao để mock findById
      categoryToUpdate.setId(categoryIdToUpdate);
      categoryToUpdate.setName(category1.getName());
      categoryToUpdate.setSlug(category1.getSlug());
      categoryToUpdate.setParent(parentCategory);
      categoryToUpdate.setBlobPath(category1.getBlobPath()); // Ảnh cũ

      CategoryResponse expectedResponse = new CategoryResponse();
      expectedResponse.setId(categoryIdToUpdate);
      expectedResponse.setName("Rau Sạch Mới");
      expectedResponse.setSlug("rau-sach-moi-slug");

      when(categoryRepository.findById(categoryIdToUpdate))
          .thenReturn(Optional.of(categoryToUpdate));
      when(categoryRepository.existsBySlugAndIdNot("rau-sach-moi-slug", categoryIdToUpdate))
          .thenReturn(false);
      // categoryMapper.updateCategoryFromRequest là void
      doNothing()
          .when(categoryMapper)
          .updateCategoryFromRequest(eq(categoryRequest), any(Category.class));
      when(categoryRepository.save(any(Category.class)))
          .thenAnswer(invocation -> invocation.getArgument(0)); // Trả về chính nó
      when(categoryMapper.toCategoryResponse(any(Category.class))).thenReturn(expectedResponse);
      doNothing().when(fileStorageService).delete(category1.getBlobPath()); // Ảnh cũ bị xóa

      CategoryResponse result = categoryService.updateCategory(categoryIdToUpdate, categoryRequest);

      assertNotNull(result);
      assertEquals("Rau Sạch Mới", result.getName());
      assertEquals("rau-sach-moi-slug", result.getSlug());
      assertNull(categoryToUpdate.getParent()); // Parent đã được bỏ
      assertEquals("images/new-image.jpg", categoryToUpdate.getBlobPath()); // Ảnh đã cập nhật
      verify(fileStorageService).delete(category1.getBlobPath());
      verify(categoryRepository).save(categoryToUpdate);
    }

    @Test
    @DisplayName("Update Category - Cannot Be Its Own Parent - Throws BadRequestException")
    void updateCategory_whenSetAsOwnParent_shouldThrowBadRequestException() {
      Integer categoryIdToUpdate = category1.getId();
      categoryRequest.setParentId(categoryIdToUpdate); // Tự làm cha

      when(categoryRepository.findById(categoryIdToUpdate)).thenReturn(Optional.of(category1));

      assertThrows(
          BadRequestException.class,
          () -> categoryService.updateCategory(categoryIdToUpdate, categoryRequest));
    }
    // ... (Thêm các test case lỗi khác cho updateCategory) ...
  }

  @Nested
  @DisplayName("Delete Category Tests")
  class DeleteCategoryTests {
    @Test
    @DisplayName("Delete Category - Success - No Products or Children, Deletes Image")
    void deleteCategory_successNoProductsOrChildren_deletesImage() {
      Integer categoryIdToDelete =
          category2.getId(); // category2 không có parent, children, product
      category2.setBlobPath("images/cu-qua.jpg");

      when(categoryRepository.findById(categoryIdToDelete)).thenReturn(Optional.of(category2));
      when(productRepository.countByCategoryId(categoryIdToDelete)).thenReturn(0L);
      // category2.getChildren() là rỗng
      doNothing().when(categoryRepository).delete(category2);
      doNothing().when(fileStorageService).delete("images/cu-qua.jpg");

      categoryService.deleteCategory(categoryIdToDelete);

      verify(categoryRepository).delete(category2);
      verify(fileStorageService).delete("images/cu-qua.jpg");
    }

    @Test
    @DisplayName("Delete Category - Category Contains Products - Throws BadRequestException")
    void deleteCategory_whenContainsProducts_shouldThrowBadRequestException() {
      Integer categoryIdToDelete = category1.getId();
      when(categoryRepository.findById(categoryIdToDelete)).thenReturn(Optional.of(category1));
      when(productRepository.countByCategoryId(categoryIdToDelete)).thenReturn(5L); // Có 5 sản phẩm

      assertThrows(
          BadRequestException.class, () -> categoryService.deleteCategory(categoryIdToDelete));
      verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    @DisplayName("Delete Category - Category Has Children - Throws BadRequestException")
    void deleteCategory_whenHasChildren_shouldThrowBadRequestException() {
      Integer categoryIdToDelete = parentCategory.getId(); // parentCategory có category1 là con
      when(categoryRepository.findById(categoryIdToDelete)).thenReturn(Optional.of(parentCategory));
      when(productRepository.countByCategoryId(categoryIdToDelete)).thenReturn(0L);
      // parentCategory.getChildren() không rỗng

      assertThrows(
          BadRequestException.class, () -> categoryService.deleteCategory(categoryIdToDelete));
    }

    @Test
    @DisplayName("Delete Category - Category Not Found - Throws ResourceNotFoundException")
    void deleteCategory_whenNotFound_shouldThrowResourceNotFoundException() {
      when(categoryRepository.findById(999)).thenReturn(Optional.empty());
      assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(999));
    }
  }
}
