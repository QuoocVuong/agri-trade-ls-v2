package com.yourcompany.agritrade.catalog.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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

  @Spy private Slugify slugify = Slugify.builder().build();

  @InjectMocks private ProductServiceImpl productService;

  private User farmerUser, adminUser, buyerUser;
  private FarmerProfile verifiedFarmerProfile, pendingFarmerProfile;
  private Category categoryEntity;
  private ProductRequest productRequest;
  private Product productEntity, productEntity2; // Thêm productEntity2
  private ProductDetailResponse productDetailResponse;
  private ProductSummaryResponse productSummaryResponse,
      productSummaryResponse2; // Thêm productSummaryResponse2

  @BeforeEach
  void setUp() {
    // Users
    farmerUser = new User();
    farmerUser.setId(1L);
    farmerUser.setEmail("farmer@example.com");
    farmerUser.setFullName("Test Farmer");
    farmerUser.setRoles(Set.of(new Role(RoleType.ROLE_FARMER)));
    farmerUser.setFarmerProfile(new FarmerProfile());

    adminUser = new User();
    adminUser.setId(2L);
    adminUser.setEmail("admin@example.com");
    adminUser.setFullName("Test Admin");
    adminUser.setRoles(Set.of(new Role(RoleType.ROLE_ADMIN)));

    buyerUser = new User();
    buyerUser.setId(3L);
    buyerUser.setEmail("buyer@example.com");
    buyerUser.setFullName("Test Buyer");
    buyerUser.setRoles(Set.of(new Role(RoleType.ROLE_CONSUMER)));

    // Farmer Profiles
    verifiedFarmerProfile = new FarmerProfile();
    verifiedFarmerProfile.setUserId(farmerUser.getId());
    verifiedFarmerProfile.setUser(farmerUser);
    verifiedFarmerProfile.setVerificationStatus(VerificationStatus.VERIFIED);
    verifiedFarmerProfile.setProvinceCode("20");
    verifiedFarmerProfile.setFarmName("Test Farm");
    farmerUser.setFarmerProfile(verifiedFarmerProfile);

    pendingFarmerProfile = new FarmerProfile();
    pendingFarmerProfile.setUserId(farmerUser.getId());
    pendingFarmerProfile.setUser(farmerUser);
    pendingFarmerProfile.setVerificationStatus(VerificationStatus.PENDING);
    pendingFarmerProfile.setProvinceCode("20");

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
    productEntity = new Product();
    productEntity.setId(1L);
    productEntity.setName(productRequest.getName());
    productEntity.setSlug(slugify.slugify(productRequest.getName()));
    productEntity.setFarmer(farmerUser);
    productEntity.setCategory(categoryEntity);
    productEntity.setProvinceCode(verifiedFarmerProfile.getProvinceCode());
    productEntity.setStatus(ProductStatus.PENDING_APPROVAL);
    productEntity.setPrice(productRequest.getPrice());
    productEntity.setUnit(productRequest.getUnit());
    productEntity.setStockQuantity(productRequest.getStockQuantity());
    productEntity.setImages(
        new HashSet<>(
            Set.of(
                new ProductImage(
                    1L,
                    productEntity,
                    "http://signed-url.com/images/ca-rot.jpg",
                    true,
                    0,
                    "images/ca-rot.jpg",
                    LocalDateTime.now()))));

    productEntity.setAverageRating(0.0f);
    productEntity.setRatingCount(0);
    productEntity.setFavoriteCount(0);
    productEntity.setCreatedAt(LocalDateTime.now());
    productEntity.setUpdatedAt(LocalDateTime.now());

    // Product Entity 2 (for list testing)
    productEntity2 = new Product();
    productEntity2.setId(2L);
    productEntity2.setName("Bí Đỏ Hồ Lô");
    productEntity2.setSlug("bi-do-ho-lo");
    productEntity2.setFarmer(farmerUser);
    productEntity2.setCategory(categoryEntity);
    productEntity2.setProvinceCode(verifiedFarmerProfile.getProvinceCode());
    productEntity2.setStatus(ProductStatus.PUBLISHED);
    productEntity2.setPrice(new BigDecimal("25000.00"));
    productEntity2.setUnit("kg");
    productEntity2.setStockQuantity(150);
    productEntity2.setImages(new HashSet<>());

    productEntity2.setAverageRating(4.5f);
    productEntity2.setRatingCount(10);
    productEntity2.setFavoriteCount(5);
    productEntity2.setCreatedAt(LocalDateTime.now().minusDays(1));
    productEntity2.setUpdatedAt(LocalDateTime.now().minusDays(1));

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
    productSummaryResponse2.setThumbnailUrl(null); // Giả sử không có ảnh default
    productSummaryResponse2.setPrice(new BigDecimal("25000.00"));
    productSummaryResponse2.setUnit("kg");
    productSummaryResponse2.setStatus(ProductStatus.PUBLISHED);

    // Mock authentication
    lenient().when(authentication.getName()).thenReturn(farmerUser.getEmail());
    lenient().when(authentication.isAuthenticated()).thenReturn(true);
  }

  private void mockAuthenticatedUser(User user) {
    lenient().when(authentication.getName()).thenReturn(user.getEmail());
    lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    if (user.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_ADMIN)) {
      Set<GrantedAuthority> authoritiesSet = new HashSet<>();
      authoritiesSet.add(new SimpleGrantedAuthority(RoleType.ROLE_ADMIN.name()));
      lenient().when(authentication.getAuthorities()).thenReturn((Collection) authoritiesSet);
    }
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

      List<Product> filteredProducts = List.of(productEntity);
      Page<Product> productPage =
          new PageImpl<>(filteredProducts, pageable, filteredProducts.size());

      // Giả sử productMapper.toProductSummaryResponse sẽ được gọi cho từng product
      when(productMapper.toProductSummaryResponse(productEntity))
          .thenReturn(productSummaryResponse);
      // Mock cho fileStorageService.getFileUrl nếu productSummaryResponse cần imageUrl
      lenient()
          .when(fileStorageService.getFileUrl(anyString()))
          .thenReturn("http://signed-url.com/some-image.jpg");

      // Quan trọng: Mock productRepository.findAll với Specification
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
      // Giả sử findRelatedProducts trả về rỗng để đơn giản
      //            when(productRepository.findTopNByCategoryIdAndIdNotAndStatus(any(), anyLong(),
      // eq(ProductStatus.PUBLISHED), any(Pageable.class)))
      //                    .thenReturn(Collections.emptyList());
      //            when(productRepository.findTopNByFarmerIdAndIdNotInAndStatus(anyLong(),
      // anyList(), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
      //                    .thenReturn(Collections.emptyList());

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
      // Giả sử product 99L thuộc farmer khác
      when(productRepository.findByIdAndFarmerId(99L, farmerUser.getId()))
          .thenReturn(Optional.empty());
      // Service sẽ ném ResourceNotFound vì query findByIdAndFarmerId không tìm thấy

      assertThrows(
          ResourceNotFoundException.class,
          () -> productService.getMyProductById(authentication, 99L));
    }

    @Test
    @DisplayName("Update My Product - Slug Change")
    void updateMyProduct_whenSlugChanges_shouldGenerateNewUniqueSlug() {
      productEntity.setStatus(ProductStatus.PUBLISHED);
      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setName("Cà Rốt Siêu Ngọt"); // Tên mới -> slug mới
      updateRequest.setCategoryId(categoryEntity.getId());
      String newExpectedSlug = "ca-rot-sieu-ngot";

      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(productRepository.existsBySlugAndIdNot(eq(newExpectedSlug), eq(productEntity.getId())))
          .thenReturn(false); // Slug mới là unique
      when(productRepository.save(any(Product.class))).thenReturn(productEntity);
      //
      // when(productRepository.findByIdWithDetails(productEntity.getId())).thenReturn(Optional.of(productEntity));
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
      //
      // when(productRepository.findByIdWithDetails(productEntity.getId())).thenReturn(Optional.of(productEntity));
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

      // --- Mock các repository và mapper khác ---
      //            when(productRepository.findByIdAndFarmerId(productEntity.getId(),
      // farmerUser.getId())).thenReturn(Optional.of(productEntity));
      when(productRepository.save(any(Product.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Mock cho lời gọi getMyProductById -> findMyProductById ->
      // productRepository.findByIdAndFarmerId
      // Giả sử productEntity là đối tượng đã được cập nhật sau khi save
      when(productRepository.findByIdAndFarmerId(eq(productEntity.getId()), eq(farmerUser.getId())))
          .thenReturn(Optional.of(productEntity)); // Mock cho lời gọi bên trong getMyProductById

      // Mock cho productMapper.toProductDetailResponse được gọi bởi getMyProductById
      when(productMapper.toProductDetailResponse(eq(productEntity)))
          .thenAnswer(
              invocation -> {
                Product p = invocation.getArgument(0);
                ProductDetailResponse res = new ProductDetailResponse();
                res.setId(p.getId());
                res.setName(p.getName());
                // ... map các trường khác ...
                if (p.getImages() != null) {
                  res.setImages(
                      p.getImages().stream()
                          .map(
                              img -> {
                                ProductImageResponse imgRes = new ProductImageResponse();
                                imgRes.setId(img.getId());
                                imgRes.setBlobPath(img.getBlobPath());
                                // Giả sử fileStorageService được gọi ở đây để lấy URL
                                lenient()
                                    .when(fileStorageService.getFileUrl(img.getBlobPath()))
                                    .thenReturn("mockedImageUrl/" + img.getBlobPath());
                                imgRes.setImageUrl(
                                    fileStorageService.getFileUrl(img.getBlobPath()));
                                imgRes.setDefault(img.isDefault());
                                imgRes.setDisplayOrder(img.getDisplayOrder());
                                return imgRes;
                              })
                          .collect(Collectors.toList()));
                }
                return res;
              });

      // --- Act ---
      ProductDetailResponse result =
          productService.updateMyProduct(authentication, productEntity.getId(), updateRequest);

      // --- Assert ---
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
      updateRequest.setCategoryId(999); // ID không tồn tại

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

      List<Product> filteredProducts = List.of(productEntity);
      Page<Product> productPage =
          new PageImpl<>(filteredProducts, pageable, filteredProducts.size());

      when(productRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(productPage);
      when(productMapper.toProductSummaryResponse(productEntity))
          .thenReturn(productSummaryResponse);
      lenient()
          .when(fileStorageService.getFileUrl(anyString()))
          .thenReturn("http://signed-url.com/image.jpg");

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
      // Mock Specification execution
      when(productRepository.findOne(any(Specification.class)))
          .thenReturn(Optional.of(productEntity));
      when(productMapper.toProductDetailResponse(productEntity)).thenReturn(productDetailResponse);
      // Mock related products
      when(productRepository.findTopNByCategoryIdAndIdNotAndStatus(
              any(), anyLong(), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
          .thenReturn(Collections.emptyList());
      when(productRepository.findTopNByFarmerIdAndIdNotInAndStatus(
              anyLong(), anyList(), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
          .thenReturn(Collections.emptyList());

      ProductDetailResponse result = productService.getPublicProductById(productEntity.getId());

      assertNotNull(result);
      assertEquals(productDetailResponse.getName(), result.getName());
      verify(productRepository).findOne(any(Specification.class));
    }

    @Test
    @DisplayName("Get Public Product By Id - Product Not Published")
    void getPublicProductById_whenProductNotPublished_shouldThrowResourceNotFound() {
      productEntity.setStatus(ProductStatus.PENDING_APPROVAL);
      when(productRepository.findOne(any(Specification.class)))
          .thenReturn(Optional.empty()); // Spec sẽ không tìm thấy

      assertThrows(
          ResourceNotFoundException.class,
          () -> productService.getPublicProductById(productEntity.getId()));
    }

    @Test
    @DisplayName("Get Public Products By Farmer Id - Success")
    void getPublicProductsByFarmerId_whenFarmerHasPublishedProducts_shouldReturnPage() {
      Pageable pageable = PageRequest.of(0, 10);
      productEntity.setStatus(ProductStatus.PUBLISHED); // Đảm bảo sản phẩm được published
      List<Product> farmerProducts = List.of(productEntity);
      Page<Product> productPage = new PageImpl<>(farmerProducts, pageable, farmerProducts.size());

      when(userRepository.existsByIdAndRoles_Name(farmerUser.getId(), RoleType.ROLE_FARMER))
          .thenReturn(true);
      when(productRepository.findByFarmerIdAndStatus(
              farmerUser.getId(), ProductStatus.PUBLISHED, pageable))
          .thenReturn(productPage);
      when(productMapper.toProductSummaryResponse(productEntity))
          .thenReturn(productSummaryResponse);
      lenient()
          .when(fileStorageService.getFileUrl(anyString()))
          .thenReturn("http://signed-url.com/image.jpg");

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
    @DisplayName("Create My Product - FileStorageService throws Exception during image processing")
    void
        createMyProduct_whenFileStorageServiceFails_shouldStillSaveProductWithoutImageErrorPropagation() {
      // Kịch bản này khó test ở unit test vì việc xử lý ảnh (updateProductImagesFromRequest)
      // xảy ra bên trong createMyProduct. Nếu FileStorageService.delete ném lỗi,
      // nó được log lại nhưng không re-throw để rollback transaction chính.
      // Chúng ta có thể kiểm tra xem product có được lưu không.

      mockAuthenticatedUser(farmerUser);
      when(farmerProfileRepository.findById(farmerUser.getId()))
          .thenReturn(Optional.of(verifiedFarmerProfile));
      when(categoryRepository.findById(categoryEntity.getId()))
          .thenReturn(Optional.of(categoryEntity));
      when(productRepository.existsBySlugAndIdNot(anyString(), any())).thenReturn(false);
      when(productMapper.requestToProduct(productRequest)).thenReturn(productEntity);
      when(productRepository.save(any(Product.class)))
          .thenReturn(productEntity); // Product vẫn được lưu
      when(productRepository.findByIdWithDetails(productEntity.getId()))
          .thenReturn(Optional.of(productEntity));
      when(productMapper.toProductDetailResponse(productEntity)).thenReturn(productDetailResponse);

      // Giả sử fileStorageService.delete ném lỗi khi xóa ảnh cũ (nếu có)
      // Trong trường hợp tạo mới, không có ảnh cũ để xóa, nên kịch bản này không áp dụng trực tiếp.
      // Nếu là update và thay đổi ảnh, thì có thể test.

      // Đối với tạo mới, nếu getFileUrl ném lỗi trong populateImageUrls (sau khi save)
      // thì DTO trả về có thể có URL placeholder.
      lenient()
          .when(fileStorageService.getFileUrl(anyString()))
          .thenThrow(new RuntimeException("Simulated storage error"));

      ProductDetailResponse result = productService.createMyProduct(authentication, productRequest);

      assertNotNull(result);
      verify(productRepository).save(any(Product.class)); // Product vẫn được lưu
      // Kiểm tra xem URL ảnh trong DTO có phải là placeholder không (nếu có logic đó trong mapper)
      // Hoặc kiểm tra log lỗi (khó trong unit test)
    }

    @Test
    @DisplayName("Update My Product - Slug conflict during update")
    void updateMyProduct_whenSlugConflict_shouldThrowBadRequest() {
      mockAuthenticatedUser(farmerUser);
      productEntity.setStatus(ProductStatus.PUBLISHED);
      ProductRequest updateRequest = new ProductRequest();
      updateRequest.setName("Conflicting Name"); // Dẫn đến slug "conflicting-name"
      updateRequest.setCategoryId(categoryEntity.getId());

      when(productRepository.findByIdAndFarmerId(productEntity.getId(), farmerUser.getId()))
          .thenReturn(Optional.of(productEntity));
      when(productRepository.existsBySlugAndIdNot(
              eq("conflicting-name"), eq(productEntity.getId())))
          .thenReturn(true); // Slug bị trùng

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
