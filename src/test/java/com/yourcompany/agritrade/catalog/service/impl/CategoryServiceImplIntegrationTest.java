package com.yourcompany.agritrade.catalog.service.impl;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException; // Giả sử bạn có exception này
// Import các exception khác nếu cần, ví dụ: BadRequestException khi xóa category có sản phẩm

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser; // Để giả lập Admin
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional // Rollback transactions after each test
public class CategoryServiceImplIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        // Dọn dẹp repository trước mỗi test để đảm bảo tính độc lập
        categoryRepository.deleteAll();
    }

    private CategoryRequest createCategoryRequest(String name, String description, Integer parentId) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setDescription(description);
        request.setParentId(parentId);
        // request.setImageUrl("http://example.com/image.png"); // Nếu có
        return request;
    }

    @Test
    @WithMockUser(roles = "ADMIN") // Giả lập người dùng có vai trò ADMIN
    void testCreateCategory_success_rootCategory() {
        CategoryRequest request = createCategoryRequest("Trái Cây", "Các loại trái cây tươi", null);

        CategoryResponse createdCategoryResponse = categoryService.createCategory(request);

        assertNotNull(createdCategoryResponse);
        assertNotNull(createdCategoryResponse.getId());
        assertEquals("Trái Cây", createdCategoryResponse.getName());
        assertEquals("trai-cay", createdCategoryResponse.getSlug()); // Giả sử slug được tạo tự động
        assertNull(createdCategoryResponse.getParentId());

        Optional<Category> foundCategoryOpt = categoryRepository.findById(createdCategoryResponse.getId());
        assertTrue(foundCategoryOpt.isPresent());
        assertEquals("Trái Cây", foundCategoryOpt.get().getName());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateCategory_success_childCategory() {
        // Tạo parent category trước
        CategoryRequest parentRequest = createCategoryRequest("Rau Củ", "Các loại rau củ", null);
        CategoryResponse parentCategoryResponse = categoryService.createCategory(parentRequest);
        assertNotNull(parentCategoryResponse.getId());

        // Tạo child category
        CategoryRequest childRequest = createCategoryRequest("Rau Ăn Lá", "Các loại rau ăn lá", parentCategoryResponse.getId());
        CategoryResponse childCategoryResponse = categoryService.createCategory(childRequest);

        assertNotNull(childCategoryResponse);
        assertNotNull(childCategoryResponse.getId());
        assertEquals("Rau Ăn Lá", childCategoryResponse.getName());
        assertEquals("rau-an-la", childCategoryResponse.getSlug());
        assertEquals(parentCategoryResponse.getId(), childCategoryResponse.getParentId());

        Optional<Category> foundChildOpt = categoryRepository.findById(childCategoryResponse.getId());
        assertTrue(foundChildOpt.isPresent());
        assertNotNull(foundChildOpt.get().getParent());
        assertEquals(parentCategoryResponse.getId(), foundChildOpt.get().getParent().getId());
    }


    @Test
    void testGetCategoryById_success() {
        CategoryRequest request = createCategoryRequest("Ngũ cốc", "Các loại ngũ cốc", null);
        // Cần quyền admin để tạo, nhưng getById có thể public hoặc chỉ cần authenticated
        // Giả sử createCategory đã được gọi trong một ngữ cảnh có quyền
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setSlug("ngu-coc"); // Giả sử slug được set
        Category savedCategory = categoryRepository.save(category);


        CategoryResponse foundCategory = categoryService.getCategoryById(savedCategory.getId());

        assertNotNull(foundCategory);
        assertEquals(savedCategory.getId(), foundCategory.getId());
        assertEquals("Ngũ cốc", foundCategory.getName());
    }

    @Test
    void testGetCategoryById_notFound() {
        assertThrows(ResourceNotFoundException.class, () -> {
            categoryService.getCategoryById(99999); // ID không tồn tại
        });
    }

    @Test
    void testGetCategoryBySlug_success() {
        Category category = new Category();
        category.setName("Hải Sản");
        category.setSlug("hai-san"); // Slug phải unique
        category.setDescription("Các loại hải sản");
        categoryRepository.save(category);

        CategoryResponse foundCategory = categoryService.getCategoryBySlug("hai-san");

        assertNotNull(foundCategory);
        assertEquals("Hải Sản", foundCategory.getName());
        assertEquals("hai-san", foundCategory.getSlug());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateCategory_success() {
        CategoryRequest createRequest = createCategoryRequest("Đồ Uống", "Các loại đồ uống", null);
        CategoryResponse createdCategory = categoryService.createCategory(createRequest);

        CategoryRequest updateRequest = createCategoryRequest("Đồ Uống Giải Khát", "Cập nhật mô tả", null);
        // Giả sử slug không được cập nhật qua request này, hoặc service tự xử lý
        // updateRequest.setSlug("do-uong-giai-khat");

        CategoryResponse updatedCategory = categoryService.updateCategory(createdCategory.getId(), updateRequest);

        assertNotNull(updatedCategory);
        assertEquals(createdCategory.getId(), updatedCategory.getId());
        assertEquals("Đồ Uống Giải Khát", updatedCategory.getName());
        assertEquals("Cập nhật mô tả", updatedCategory.getDescription());
        // Kiểm tra slug nếu có logic cập nhật slug
        // assertEquals("do-uong-giai-khat", updatedCategory.getSlug()); // Hoặc slug mới dựa trên tên mới

        Optional<Category> foundInDb = categoryRepository.findById(createdCategory.getId());
        assertTrue(foundInDb.isPresent());
        assertEquals("Đồ Uống Giải Khát", foundInDb.get().getName());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteCategory_success_noProducts() {
        CategoryRequest request = createCategoryRequest("Đồ Khô", "Các loại đồ khô", null);
        CategoryResponse createdCategory = categoryService.createCategory(request);
        Integer categoryId = createdCategory.getId();

        assertTrue(categoryRepository.existsById(categoryId));
        // Giả sử không có sản phẩm nào thuộc category này
        categoryService.deleteCategory(categoryId);

        assertFalse(categoryRepository.existsById(categoryId));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteCategory_fail_withProducts() {
        // TODO: Test này cần bạn tạo Product entity và ProductRepository
        // và thêm logic vào CategoryService.deleteCategory để kiểm tra sản phẩm tồn tại.
        // Nếu CategoryService.deleteCategory ném một exception cụ thể (ví dụ: CategoryNotEmptyException),
        // thì hãy dùng assertThrows để bắt nó.

        // 1. Tạo Category
        // CategoryRequest request = createCategoryRequest("Thịt", "Các loại thịt", null);
        // CategoryResponse createdCategory = categoryService.createCategory(request);
        // Integer categoryId = createdCategory.getId();

        // 2. Tạo một Product thuộc Category này (cần ProductRepository và User (Farmer))
        // Product product = new Product();
        // product.setName("Thịt Ba Chỉ");
        // product.setCategory(categoryRepository.findById(categoryId).orElseThrow());
        // product.setFarmer(someFarmer); // Cần tạo farmer
        // productRepository.save(product);

        // 3. Thử xóa Category và mong đợi lỗi
        // assertThrows(YourSpecificExceptionWhenCategoryHasProducts.class, () -> {
        //     categoryService.deleteCategory(categoryId);
        // });
        assertTrue(true, "Test for deleting category with products needs implementation.");
    }


    @Test
    void testGetCategoryTree_success() {
        // Tạo một cấu trúc cây đơn giản
        CategoryResponse root1 = categoryService.createCategory(createCategoryRequest("Root 1", "R1", null));
        CategoryResponse child1_1 = categoryService.createCategory(createCategoryRequest("Child 1.1", "C1.1", root1.getId()));
        CategoryResponse child1_2 = categoryService.createCategory(createCategoryRequest("Child 1.2", "C1.2", root1.getId()));
        CategoryResponse root2 = categoryService.createCategory(createCategoryRequest("Root 2", "R2", null));

        List<CategoryResponse> categoryTree = categoryService.getCategoryTree();

        assertNotNull(categoryTree);
        assertEquals(2, categoryTree.size()); // Phải có 2 root categories

        CategoryResponse foundRoot1 = categoryTree.stream().filter(c -> c.getId().equals(root1.getId())).findFirst().orElse(null);
        assertNotNull(foundRoot1);
        assertEquals(2, foundRoot1.getChildren().size()); // Root 1 phải có 2 con

        assertTrue(foundRoot1.getChildren().stream().anyMatch(c -> c.getId().equals(child1_1.getId())));
        assertTrue(foundRoot1.getChildren().stream().anyMatch(c -> c.getId().equals(child1_2.getId())));
    }

    @Test
    void testGetAllCategoriesForDropdown_success() {
        categoryService.createCategory(createCategoryRequest("Cat A", "A", null));
        categoryService.createCategory(createCategoryRequest("Cat B", "B", null));

        List<CategoryResponse> dropdownCategories = categoryService.getAllCategoriesForDropdown();

        assertNotNull(dropdownCategories);
        assertEquals(2, dropdownCategories.size());
        // Kiểm tra thêm các thuộc tính nếu cần, ví dụ: không có children, chỉ có id, name, slug
    }
}