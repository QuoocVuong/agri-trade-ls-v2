package com.yourcompany.agritrade.catalog.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.slugify.Slugify;
import com.yourcompany.agritrade.catalog.domain.*;
import com.yourcompany.agritrade.catalog.dto.request.ProductImageRequest;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.*;
import com.yourcompany.agritrade.catalog.mapper.ProductImageMapper;
import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductImageRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private UserRepository userRepository;
  @Mock private FarmerProfileRepository farmerProfileRepository;
  @Mock private ProductImageRepository productImageRepository;
  @Mock private ProductMapper productMapper;
  @Mock private ProductImageMapper productImageMapper;
  @Mock private FileStorageService fileStorageService;
  @Mock private NotificationService notificationService;
  @Mock private EmailService emailService;
  @Mock private Authentication authentication;

  // SỬA LỖI: Thêm mock cho ReviewService
  @Mock private ReviewService reviewService;

  @Spy private Slugify slugify = Slugify.builder().build();

  // SỬA LỖI: Thêm MockedStatic để quản lý mock cho lớp tiện ích SecurityUtils
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private ProductServiceImpl productService;

  private User farmerUser, adminUser, buyerUser;
  private FarmerProfile verifiedFarmerProfile;
  private Category categoryEntity;
  private ProductRequest productRequest;
  private Product productEntity, productEntity2;
  private ProductDetailResponse productDetailResponse;
  private ProductSummaryResponse productSummaryResponse, productSummaryResponse2;

  @BeforeEach
  void setUp() {
    // SỬA LỖI: Khởi tạo mock static cho SecurityUtils trước mỗi test
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    // Users
    farmerUser =
        User.builder()
            .id(1L)
            .email("farmer@example.com")
            .fullName("Test Farmer")
            .roles(Set.of(new Role(RoleType.ROLE_FARMER)))
            .build();
    adminUser =
        User.builder()
            .id(2L)
            .email("admin@example.com")
            .fullName("Test Admin")
            .roles(Set.of(new Role(RoleType.ROLE_ADMIN)))
            .build();
    buyerUser =
        User.builder()
            .id(3L)
            .email("buyer@example.com")
            .fullName("Test Buyer")
            .roles(Set.of(new Role(RoleType.ROLE_CONSUMER)))
            .build();

    // Farmer Profile
    verifiedFarmerProfile = new FarmerProfile();
    verifiedFarmerProfile.setUserId(farmerUser.getId());
    verifiedFarmerProfile.setUser(farmerUser);
    verifiedFarmerProfile.setVerificationStatus(VerificationStatus.VERIFIED);
    verifiedFarmerProfile.setProvinceCode("20");
    verifiedFarmerProfile.setFarmName("Test Farm");
    farmerUser.setFarmerProfile(verifiedFarmerProfile);

    // Category
    categoryEntity = new Category();
    categoryEntity.setId(100);
    categoryEntity.setName("Rau Củ");
    categoryEntity.setSlug("rau-cu");

    // Product Request
    productRequest = new ProductRequest();
    productRequest.setName("Cà Rốt Đà Lạt");
    productRequest.setDescription("Cà rốt tươi ngon từ Đà Lạt");
    productRequest.setPrice(new BigDecimal("15000.00"));
    productRequest.setUnit("kg");
    productRequest.setStockQuantity(200);
    productRequest.setCategoryId(categoryEntity.getId());
    productRequest.setB2bEnabled(false);

    ProductImageRequest imageReq = new ProductImageRequest();
    imageReq.setBlobPath("images/ca-rot.jpg");
    imageReq.setImageUrl("http://localhost/api/files/download/images/ca-rot.jpg");
    imageReq.setIsDefault(true);
    imageReq.setDisplayOrder(0);
    productRequest.setImages(List.of(imageReq));

    // Product Entity 1
    productEntity =
        Product.builder()
            .id(1L)
            .name(productRequest.getName())
            .slug(slugify.slugify(productRequest.getName()))
            .farmer(farmerUser)
            .category(categoryEntity)
            .provinceCode(verifiedFarmerProfile.getProvinceCode())
            .status(ProductStatus.PENDING_APPROVAL)
            .price(productRequest.getPrice())
            .unit(productRequest.getUnit())
            .stockQuantity(productRequest.getStockQuantity())
            .images(
                new HashSet<>(
                    Set.of(
                        new ProductImage(
                            1L,
                            null,
                            "http://signed-url.com/images/ca-rot.jpg",
                            true,
                            0,
                            "images/ca-rot.jpg",
                            LocalDateTime.now()))))
            .averageRating(0.0f)
            .ratingCount(0)
            .favoriteCount(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // Product Entity 2
    productEntity2 =
        Product.builder()
            .id(2L)
            .name("Bí Đỏ Hồ Lô")
            .slug("bi-do-ho-lo")
            .farmer(farmerUser)
            .category(categoryEntity)
            .provinceCode(verifiedFarmerProfile.getProvinceCode())
            .status(ProductStatus.PUBLISHED)
            .price(new BigDecimal("25000.00"))
            .unit("kg")
            .stockQuantity(150)
            .images(new HashSet<>())
            .averageRating(4.5f)
            .ratingCount(10)
            .favoriteCount(5)
            .createdAt(LocalDateTime.now().minusDays(1))
            .updatedAt(LocalDateTime.now().minusDays(1))
            .build();

    // Product Detail Response
    productDetailResponse = new ProductDetailResponse();
    productDetailResponse.setId(1L);
    productDetailResponse.setName(productRequest.getName());
    productDetailResponse.setStatus(ProductStatus.PENDING_APPROVAL);
    CategoryInfoResponse catInfo = new CategoryInfoResponse();
    catInfo.setId(categoryEntity.getId());
    catInfo.setName(categoryEntity.getName());
    productDetailResponse.setCategory(catInfo);
    FarmerInfoResponse farmerInfo = new FarmerInfoResponse();
    farmerInfo.setFarmerId(farmerUser.getId());
    farmerInfo.setFarmName(verifiedFarmerProfile.getFarmName());
    productDetailResponse.setFarmer(farmerInfo);
    productDetailResponse.setProvinceCode(verifiedFarmerProfile.getProvinceCode());
    ProductImageResponse imgRes = new ProductImageResponse();
    imgRes.setBlobPath(imageReq.getBlobPath());
    imgRes.setImageUrl("http://signed-url.com/images/ca-rot.jpg");
    productDetailResponse.setImages(List.of(imgRes));

    // Product Summary Response 1
    productSummaryResponse = new ProductSummaryResponse();
    productSummaryResponse.setId(1L);
    productSummaryResponse.setName(productRequest.getName());
    productSummaryResponse.setThumbnailUrl("http://signed-url.com/images/ca-rot.jpg");
    productSummaryResponse.setPrice(productRequest.getPrice());
    productSummaryResponse.setUnit(productRequest.getUnit());
    productSummaryResponse.setStatus(ProductStatus.PENDING_APPROVAL);

    // Product Summary Response 2
    productSummaryResponse2 = new ProductSummaryResponse();
    productSummaryResponse2.setId(2L);
    productSummaryResponse2.setName("Bí Đỏ Hồ Lô");
    productSummaryResponse2.setThumbnailUrl(null);
    productSummaryResponse2.setPrice(new BigDecimal("25000.00"));
    productSummaryResponse2.setUnit("kg");
    productSummaryResponse2.setStatus(ProductStatus.PUBLISHED);

    lenient()
        .when(fileStorageService.getFileUrl(anyString()))
        .thenAnswer(invocation -> "mockedUrl/" + invocation.getArgument(0));
  }

  // SỬA LỖI: Thêm tearDown để đóng mock static sau mỗi test
  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  private void mockAuthenticatedUser(User user) {
    // SỬA LỖI: Mock SecurityUtils thay vì các mock không được dùng đến
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(user);
  }

  @Nested
  @DisplayName("Farmer Product Management")
  class FarmerProductManagement {

    @BeforeEach
    void farmerSetup() {
      mockAuthenticatedUser(farmerUser);
    }

    @Test
    @DisplayName("Get My Products - With Keyword and Status Filters")
    void getMyProducts_withKeywordAndStatus_shouldReturnFilteredProducts() {
      Pageable pageable = PageRequest.of(0, 10);
      String keyword = "Cà Rốt";
      ProductStatus status = ProductStatus.PENDING_APPROVAL;

      Page<Product> productPage = new PageImpl<>(List.of(productEntity), pageable, 1);

      when(productMapper.toProductSummaryResponse(productEntity))
          .thenReturn(productSummaryResponse);
      when(productRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(productPage);

      Page<ProductSummaryResponse> result =
          productService.getMyProducts(authentication, keyword, status, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(productSummaryResponse.getName(), result.getContent().get(0).getName());
      verify(productRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Get My Product By Id - Success")
    void getMyProductById_whenProductExistsAndOwned_shouldReturnProductDetail() {
      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(productMapper.toProductDetailResponse(productEntity)).thenReturn(productDetailResponse);

      ProductDetailResponse result =
          productService.getMyProductById(authentication, productEntity.getId());

      assertNotNull(result);
      assertEquals(productDetailResponse.getName(), result.getName());
      verify(productRepository).findByIdAndFarmerId(productEntity.getId(), farmerUser.getId());
    }

    @Test
    @DisplayName("Get My Product By Id - Product Not Found")
    void getMyProductById_whenProductNotFound_shouldThrowResourceNotFoundException() {
      when(productRepository.findByIdAndFarmerId(99L, farmerUser.getId()))
          .thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> productService.getMyProductById(authentication, 99L));
    }

    @Test
    @DisplayName("Get My Product By Id - Product Not Owned by Farmer")
    void getMyProductById_whenProductNotOwned_shouldThrowResourceNotFoundException() {
      when(productRepository.findByIdAndFarmerId(99L, farmerUser.getId()))
          .thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> productService.getMyProductById(authentication, 99L));
    }

    @Test
    @DisplayName("Update My Product - Slug Change")
    void updateMyProduct_whenSlugChanges_shouldGenerateNewUniqueSlug() {
      productEntity.setStatus(ProductStatus.PUBLISHED);
      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setName("Cà Rốt Siêu Ngọt");
      updateRequest.setCategoryId(categoryEntity.getId());
      String newExpectedSlug = "ca-rot-sieu-ngot";

      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(productRepository.existsBySlugAndIdNot(eq(newExpectedSlug), eq(productEntity.getId())))
          .thenReturn(false);
      when(productRepository.save(any(Product.class))).thenReturn(productEntity);
      when(productMapper.toProductDetailResponse(productEntity)).thenReturn(productDetailResponse);

      productService.updateMyProduct(authentication, productEntity.getId(), updateRequest);

      assertEquals(newExpectedSlug, productEntity.getSlug());
      verify(productRepository).save(productEntity);
    }

    @Test
    @DisplayName("Update My Product - Category Change")
    void updateMyProduct_whenCategoryChanges_shouldUpdateCategory() {
      productEntity.setStatus(ProductStatus.PUBLISHED);
      Category newCategory = new Category();
      newCategory.setId(200);
      newCategory.setName("Củ Quả");
      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setCategoryId(newCategory.getId());

      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(categoryRepository.findById(newCategory.getId())).thenReturn(Optional.of(newCategory));
      when(productRepository.save(any(Product.class))).thenReturn(productEntity);
      when(productMapper.toProductDetailResponse(productEntity)).thenReturn(productDetailResponse);

      productService.updateMyProduct(authentication, productEntity.getId(), updateRequest);

      assertEquals(newCategory, productEntity.getCategory());
      verify(productRepository).save(productEntity);
    }

    @Test
    @DisplayName("Update My Product - Image Updates (Add, Remove, Reorder)")
    void updateMyProduct_whenImagesUpdated_shouldReflectChanges() {
      productEntity.setStatus(ProductStatus.PUBLISHED);
      ProductImage existingImageEntity =
          new ProductImage(1L, productEntity, "url1", true, 0, "path1", LocalDateTime.now());
      productEntity.setImages(new HashSet<>(Set.of(existingImageEntity)));

      ProductImageRequest newImageReq = new ProductImageRequest();
      newImageReq.setBlobPath("path2");
      newImageReq.setImageUrl("url2");
      newImageReq.setIsDefault(false);
      newImageReq.setDisplayOrder(1);

      ProductImageRequest updatedExistingImageReq = new ProductImageRequest();
      updatedExistingImageReq.setId(existingImageEntity.getId());
      updatedExistingImageReq.setBlobPath("path1");
      updatedExistingImageReq.setImageUrl("url1");
      updatedExistingImageReq.setIsDefault(true);
      updatedExistingImageReq.setDisplayOrder(0);

      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setCategoryId(categoryEntity.getId());
      updateRequest.setImages(List.of(updatedExistingImageReq, newImageReq));

      ProductImage newImageEntityMappedFromRequest = new ProductImage();
      newImageEntityMappedFromRequest.setProduct(productEntity);
      newImageEntityMappedFromRequest.setBlobPath(newImageReq.getBlobPath());
      newImageEntityMappedFromRequest.setDefault(newImageReq.getIsDefault());
      newImageEntityMappedFromRequest.setDisplayOrder(newImageReq.getDisplayOrder());
      when(productImageMapper.requestToProductImage(eq(newImageReq)))
          .thenReturn(newImageEntityMappedFromRequest);

      ProductImage imageEntityMappedFromUpdatedExistingReq = new ProductImage();
      imageEntityMappedFromUpdatedExistingReq.setProduct(productEntity);
      imageEntityMappedFromUpdatedExistingReq.setBlobPath(updatedExistingImageReq.getBlobPath());
      imageEntityMappedFromUpdatedExistingReq.setDefault(updatedExistingImageReq.getIsDefault());
      imageEntityMappedFromUpdatedExistingReq.setDisplayOrder(
          updatedExistingImageReq.getDisplayOrder());
      when(productImageMapper.requestToProductImage(eq(updatedExistingImageReq)))
          .thenReturn(imageEntityMappedFromUpdatedExistingReq);

      when(productRepository.save(any(Product.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(productRepository.findByIdAndFarmerId(eq(productEntity.getId()), eq(farmerUser.getId())))
          .thenReturn(Optional.of(productEntity));
      when(productMapper.toProductDetailResponse(eq(productEntity)))
          .thenReturn(productDetailResponse);

      ProductDetailResponse result =
          productService.updateMyProduct(authentication, productEntity.getId(), updateRequest);

      assertNotNull(result);
      Set<ProductImage> finalImagesInProduct = productEntity.getImages();
      assertEquals(2, finalImagesInProduct.size());

      assertTrue(
          finalImagesInProduct.stream()
              .anyMatch(
                  img ->
                      "path2".equals(img.getBlobPath())
                          && !img.isDefault()
                          && img.getDisplayOrder() == 1
                          && img.getProduct().equals(productEntity)));
      assertTrue(
          finalImagesInProduct.stream()
              .anyMatch(
                  img ->
                      "path1".equals(img.getBlobPath())
                          && img.isDefault()
                          && img.getDisplayOrder() == 0));

      verify(productImageRepository, never()).delete(any(ProductImage.class));
      verify(productImageRepository, never()).deleteAll(anyList());
      verify(productImageRepository, never()).deleteAllByIdInBatch(anyList());

      verify(productImageMapper, times(1)).requestToProductImage(eq(newImageReq));
      verify(productImageMapper, times(1)).requestToProductImage(eq(updatedExistingImageReq));
    }

    @Test
    @DisplayName("Update My Product - Invalid Category ID")
    void updateMyProduct_whenInvalidCategoryId_shouldThrowResourceNotFound() {
      productEntity.setStatus(ProductStatus.PUBLISHED);
      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setCategoryId(999);

      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(categoryRepository.findById(999)).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () ->
              productService.updateMyProduct(authentication, productEntity.getId(), updateRequest));
    }
  }

  @Nested
  @DisplayName("Admin Product Management")
  class AdminProductManagement {
    @BeforeEach
    void adminSetup() {
      mockAuthenticatedUser(adminUser);
    }

    @Test
    @DisplayName("Get All Products For Admin - With Filters")
    void getAllProductsForAdmin_withFilters_shouldReturnFilteredPage() {
      Pageable pageable = PageRequest.of(0, 10);
      String keyword = "Cà Rốt";
      String statusString = "PENDING_APPROVAL";
      Integer categoryId = categoryEntity.getId();
      Long farmerIdParam = farmerUser.getId();

      Page<Product> productPage = new PageImpl<>(List.of(productEntity), pageable, 1);

      when(productRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(productPage);
      when(productMapper.toProductSummaryResponse(productEntity))
          .thenReturn(productSummaryResponse);

      Page<ProductSummaryResponse> result =
          productService.getAllProductsForAdmin(
              keyword, statusString, categoryId, farmerIdParam, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(productSummaryResponse.getName(), result.getContent().get(0).getName());
      verify(productRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Get Product By Id For Admin - Product Not Found")
    void getProductByIdForAdmin_whenProductNotFound_shouldThrowResourceNotFound() {
      when(productRepository.findById(99L)).thenReturn(Optional.empty());
      assertThrows(
          ResourceNotFoundException.class, () -> productService.getProductByIdForAdmin(99L));
    }
  }

  @Nested
  @DisplayName("Public Product Viewing")
  class PublicProductViewing {

    @Test
    @DisplayName("Get Public Product By Id - Success")
    void getPublicProductById_whenProductPublished_shouldReturnDetails() {
      productEntity.setStatus(ProductStatus.PUBLISHED);
      when(productRepository.findOne(any(Specification.class)))
          .thenReturn(Optional.of(productEntity));
      when(productMapper.toProductDetailResponse(productEntity)).thenReturn(productDetailResponse);
      when(productRepository.findTopNByCategoryIdAndIdNotAndStatus(
              any(), anyLong(), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
          .thenReturn(Collections.emptyList());
      when(productRepository.findTopNByFarmerIdAndIdNotInAndStatus(
              anyLong(), anyList(), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
          .thenReturn(Collections.emptyList());

      // SỬA LỖI: Mock cho reviewService
      Page<ReviewResponse> emptyReviewPage = Page.empty();
      when(reviewService.getApprovedReviewsByProduct(
              eq(productEntity.getId()), any(Pageable.class)))
          .thenReturn(emptyReviewPage);

      ProductDetailResponse result = productService.getPublicProductById(productEntity.getId());

      assertNotNull(result);
      assertEquals(productDetailResponse.getName(), result.getName());
      verify(productRepository).findOne(any(Specification.class));
      verify(reviewService)
          .getApprovedReviewsByProduct(eq(productEntity.getId()), any(Pageable.class));
    }

    @Test
    @DisplayName("Get Public Product By Id - Product Not Published")
    void getPublicProductById_whenProductNotPublished_shouldThrowResourceNotFound() {
      productEntity.setStatus(ProductStatus.PENDING_APPROVAL);
      when(productRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> productService.getPublicProductById(productEntity.getId()));
    }

    @Test
    @DisplayName("Get Public Products By Farmer Id - Success")
    void getPublicProductsByFarmerId_whenFarmerHasPublishedProducts_shouldReturnPage() {
      Pageable pageable = PageRequest.of(0, 10);
      productEntity.setStatus(ProductStatus.PUBLISHED);
      Page<Product> productPage = new PageImpl<>(List.of(productEntity), pageable, 1);

      when(userRepository.existsByIdAndRoles_Name(farmerUser.getId(), RoleType.ROLE_FARMER))
          .thenReturn(true);
      when(productRepository.findByFarmerIdAndStatus(
              farmerUser.getId(), ProductStatus.PUBLISHED, pageable))
          .thenReturn(productPage);
      when(productMapper.toProductSummaryResponse(productEntity))
          .thenReturn(productSummaryResponse);

      Page<ProductSummaryResponse> result =
          productService.getPublicProductsByFarmerId(farmerUser.getId(), pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(productSummaryResponse.getName(), result.getContent().get(0).getName());
      verify(productRepository)
          .findByFarmerIdAndStatus(farmerUser.getId(), ProductStatus.PUBLISHED, pageable);
    }
  }

  @Nested
  @DisplayName("Error Handling and Edge Cases")
  class ErrorHandlingAndEdgeCases {

    @Test
    @DisplayName("Create My Product - Farmer Profile Not Verified")
    void createMyProduct_whenFarmerProfileNotVerified_shouldThrowBadRequest() {
      FarmerProfile pendingProfile = new FarmerProfile();
      pendingProfile.setVerificationStatus(VerificationStatus.PENDING);
      farmerUser.setFarmerProfile(pendingProfile);
      mockAuthenticatedUser(farmerUser);
      when(farmerProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.of(pendingProfile));

      assertThrows(
          BadRequestException.class,
          () -> productService.createMyProduct(authentication, productRequest));
    }

    @Test
    @DisplayName("Update My Product - Slug conflict during update")
    void updateMyProduct_whenSlugConflict_shouldThrowBadRequest() {
      mockAuthenticatedUser(farmerUser);
      productEntity.setStatus(ProductStatus.PUBLISHED);
      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setName("Conflicting Name");
      updateRequest.setCategoryId(categoryEntity.getId());

      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(productRepository.existsBySlugAndIdNot(
              eq("conflicting-name"), eq(productEntity.getId())))
          .thenReturn(true);

      BadRequestException ex =
          assertThrows(
              BadRequestException.class,
              () ->
                  productService.updateMyProduct(
                      authentication, productEntity.getId(), updateRequest));
      assertTrue(ex.getMessage().contains("Slug 'conflicting-name' đã được sử dụng."));
    }
  }
}
