package com.yourcompany.agritrade.catalog.service.impl;

import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.request.ProductImageRequest;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductImageResponse;
import com.yourcompany.agritrade.catalog.mapper.ProductImageMapper;
import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
// Bỏ ProductImageRepository nếu không dùng trực tiếp
// import com.yourcompany.agritrade.catalog.repository.ProductImageRepository;
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
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import com.github.slugify.Slugify;


import java.math.BigDecimal;
// import java.util.Collections; // Không cần nếu dùng List.of
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyLong; // Không dùng trong file này
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private FarmerProfileRepository farmerProfileRepository;
    // @Mock private ProductImageRepository productImageRepository; // Bỏ nếu không dùng
    @Mock private ProductMapper productMapper;
    // @Mock private ProductImageMapper productImageMapper; // Bỏ nếu không dùng
    @Mock private FileStorageService fileStorageService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private Authentication authentication;
    @Mock private RoleRepository roleRepository;

    @Spy
    private Slugify slugify = Slugify.builder().build();


    @InjectMocks
    private ProductServiceImpl productService;

    private User farmerUser;
    private FarmerProfile verifiedFarmerProfile;
    private Category categoryEntity;
    private ProductRequest productRequest;
    private ProductDetailResponse productDetailResponse;

    private Role farmerRoleEntity;

    @BeforeEach
    void setUp() {
        farmerUser = new User();
        farmerUser.setId(1L);
        farmerUser.setEmail("farmer@example.com");
        farmerUser.setFullName("Test Farmer");


        // *** SỬA LỖI: Tạo và gán Role cho farmerUser ***
        farmerRoleEntity = new Role(RoleType.ROLE_FARMER); // Giả sử Role có constructor này
        farmerRoleEntity.setId(123); // Gán ID giả lập cho Role nếu cần
        Set<Role> roles = new HashSet<>();
        roles.add(farmerRoleEntity);
        farmerUser.setRoles(roles); // Gán vai trò cho user
        // *********************************************

        verifiedFarmerProfile = new FarmerProfile();
        verifiedFarmerProfile.setUserId(farmerUser.getId());
        verifiedFarmerProfile.setUser(farmerUser);
        verifiedFarmerProfile.setVerificationStatus(VerificationStatus.VERIFIED);
        verifiedFarmerProfile.setProvinceCode("20");
        verifiedFarmerProfile.setFarmName("Test Farm");

        categoryEntity = new Category();
        categoryEntity.setId(100);
        categoryEntity.setName("Rau Củ");
        categoryEntity.setSlug("rau-cu");

        productRequest = new ProductRequest();
        productRequest.setName("Cà Rốt Đà Lạt");
        productRequest.setDescription("Cà rốt tươi ngon từ Đà Lạt");
        productRequest.setPrice(new BigDecimal("15000.00"));
        productRequest.setUnit("kg");
        productRequest.setStockQuantity(200);
        productRequest.setCategoryId(categoryEntity.getId());

        ProductImageRequest imageReq = new ProductImageRequest();
        imageReq.setBlobPath("images/ca-rot.jpg");
        imageReq.setImageUrl("http://localhost/api/files/download/images/ca-rot.jpg");
        imageReq.setIsDefault(true);
        imageReq.setDisplayOrder(0);
        productRequest.setImages(List.of(imageReq));

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

        // *** SỬA LỖI: Mock authentication đầy đủ hơn ***
        // Sử dụng lenient() cho các mock trong setUp nếu chúng không được dùng trong TẤT CẢ test cases
        lenient().when(authentication.getName()).thenReturn(farmerUser.getEmail());
        lenient().when(authentication.isAuthenticated()).thenReturn(true); // QUAN TRỌNG
    }



    @Test
    @DisplayName("TC4.2: Create My Product - Fails if Farmer Not Verified")
    void createMyProduct_whenFarmerNotVerified_shouldThrowBadRequestException() {
        // Arrange
        verifiedFarmerProfile.setVerificationStatus(VerificationStatus.PENDING);
        when(userRepository.findByEmail(farmerUser.getEmail())).thenReturn(Optional.of(farmerUser));
        when(farmerProfileRepository.findById(farmerUser.getId())).thenReturn(Optional.of(verifiedFarmerProfile));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            productService.createMyProduct(authentication, productRequest);
        });
        assertEquals("Your farmer profile is not verified yet. Cannot create products.", exception.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("TC4.3: Create My Product - Fails if Category Not Found")
    void createMyProduct_whenCategoryNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(userRepository.findByEmail(farmerUser.getEmail())).thenReturn(Optional.of(farmerUser));
        when(farmerProfileRepository.findById(farmerUser.getId())).thenReturn(Optional.of(verifiedFarmerProfile));
        when(categoryRepository.findById(categoryEntity.getId())).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            productService.createMyProduct(authentication, productRequest);
        });
        assertEquals(String.format("Category not found with id : '%s'", categoryEntity.getId()), exception.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }


    @Test
    @DisplayName("TC6.1: Approve Product - Success by Admin")
    void approveProduct_whenProductIsPendingAndAdminApproves_shouldSetStatusToPublishedAndNotify() {
        // Arrange
        Product pendingProduct = new Product();
        pendingProduct.setId(1L);
        pendingProduct.setName("Pending Product");
        pendingProduct.setStatus(ProductStatus.PENDING_APPROVAL);
        pendingProduct.setRejectReason("Old reason");
        User productFarmer = new User();
        productFarmer.setId(2L);
        productFarmer.setEmail("productfarmer@example.com");
        pendingProduct.setFarmer(productFarmer);

        Product publishedProductAfterSave = new Product();
        publishedProductAfterSave.setId(1L);
        publishedProductAfterSave.setName("Pending Product");
        publishedProductAfterSave.setStatus(ProductStatus.PUBLISHED);
        publishedProductAfterSave.setRejectReason(null);
        publishedProductAfterSave.setFarmer(productFarmer);

        ProductDetailResponse expectedDto = new ProductDetailResponse();
        expectedDto.setId(1L);
        expectedDto.setName("Pending Product");
        expectedDto.setStatus(ProductStatus.PUBLISHED);

        when(productRepository.findById(1L)).thenReturn(Optional.of(pendingProduct));
        when(productRepository.save(any(Product.class))).thenReturn(publishedProductAfterSave);
        when(userRepository.findById(productFarmer.getId())).thenReturn(Optional.of(productFarmer));
        doNothing().when(notificationService).sendProductApprovedNotification(any(Product.class), any(User.class));
        doNothing().when(emailService).sendProductApprovedEmailToFarmer(any(Product.class), any(User.class));
        when(productRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(publishedProductAfterSave));
        when(productMapper.toProductDetailResponse(publishedProductAfterSave)).thenReturn(expectedDto);

        // Act
        ProductDetailResponse result = productService.approveProduct(1L);

        // Assert
        assertNotNull(result);
        assertEquals(ProductStatus.PUBLISHED, result.getStatus());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();

        assertEquals(ProductStatus.PUBLISHED, savedProduct.getStatus());
        assertNull(savedProduct.getRejectReason());

        verify(notificationService).sendProductApprovedNotification(eq(publishedProductAfterSave), eq(productFarmer));
        verify(emailService).sendProductApprovedEmailToFarmer(eq(publishedProductAfterSave), eq(productFarmer));
    }

    @Test
    @DisplayName("TC6.2: Approve Product - Fails if Product Not Pending/Rejected/Draft")
    void approveProduct_whenProductIsNotPendingOrRejectedOrDraft_shouldThrowBadRequestException() {
        // Arrange
        Product publishedProduct = new Product();
        publishedProduct.setId(1L);
        publishedProduct.setStatus(ProductStatus.PUBLISHED);
        when(productRepository.findById(1L)).thenReturn(Optional.of(publishedProduct));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            productService.approveProduct(1L);
        });
        assertTrue(exception.getMessage().contains("Product cannot be approved from its current status: PUBLISHED"));
        verify(productRepository, never()).save(any(Product.class));
    }
}