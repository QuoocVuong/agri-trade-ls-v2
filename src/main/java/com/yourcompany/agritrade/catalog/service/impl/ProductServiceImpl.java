package com.yourcompany.agritrade.catalog.service.impl;

import com.github.slugify.Slugify;
import com.yourcompany.agritrade.catalog.domain.*;
import com.yourcompany.agritrade.catalog.dto.request.ProductImageRequest;
import com.yourcompany.agritrade.catalog.dto.request.ProductPricingTierRequest;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.mapper.ProductImageMapper;
import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.catalog.mapper.ProductPricingTierMapper; // Import mapper bậc giá
import com.yourcompany.agritrade.catalog.repository.*;
import com.yourcompany.agritrade.catalog.repository.specification.ProductSpecifications; // Import Specifications
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus; // Import VerificationStatus
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FarmerProfileRepository farmerProfileRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductPricingTierRepository pricingTierRepository; // Inject nếu dùng
    private final ProductMapper productMapper;
    private final ProductPricingTierMapper pricingTierMapper; // Inject mapper bậc giá
    private final Slugify slugify = Slugify.builder().build();
    private final ProductImageMapper productImageMapper;
    private final FileStorageService fileStorageService;
    private static final int RELATED_PRODUCTS_LIMIT = 4; // Số lượng sản phẩm liên quan muốn hiển thị

    // --- Farmer Methods ---

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getMyProducts(Authentication authentication, Pageable pageable) {
        User farmer = getUserFromAuthentication(authentication);
        // findByFarmerId đã tự lọc is_deleted=false nhờ @Where
        return productRepository.findByFarmerId(farmer.getId(), pageable)
                .map(productMapper::toProductSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getMyProductById(Authentication authentication, Long productId) {
        User farmer = getUserFromAuthentication(authentication);
        Product product = findMyProductById(productId, farmer.getId());
        ProductDetailResponse response = productMapper.toProductDetailResponse(product);
        // Lấy sản phẩm liên quan (có thể bỏ qua cho chính farmer xem)
        // response.setRelatedProducts(findRelatedProducts(product));
        return response;
    }

    @Override
    @Transactional
    public ProductDetailResponse createMyProduct(Authentication authentication, ProductRequest request) {
        User farmer = getUserFromAuthentication(authentication);
        validateFarmer(farmer); // Kiểm tra vai trò và profile

        FarmerProfile farmerProfile = farmerProfileRepository.findById(farmer.getId())
                .orElseThrow(() -> new BadRequestException("Farmer profile not found. Please complete your profile."));

        // Kiểm tra trạng thái duyệt của Farmer
        if (farmerProfile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new BadRequestException("Your farmer profile is not verified yet. Cannot create products.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        String slug = generateUniqueSlug(request.getName(), null); // Tạo slug unique

        Product product = productMapper.requestToProduct(request);
        product.setSlug(slug);
        product.setFarmer(farmer);
        product.setCategory(category);
        product.setProvinceCode(farmerProfile.getProvinceCode());
        product.setStatus(ProductStatus.PENDING_APPROVAL); // Luôn bắt đầu là DRAFT

        // Xử lý ảnh thông minh hơn (từ request DTO)
        updateProductImagesFromRequest(product, request.getImages()); // Gọi helper mới

        // Xử lý ảnh và bậc giá
//        updateProductImages(product, request.getImageUrls());
        updatePricingTiers(product, request.getIsB2bAvailable(), request.getPricingTiers());

        Product savedProduct = productRepository.save(product);
        log.info("Product created with id: {} by farmer: {}", savedProduct.getId(), farmer.getId());
        // Cần load lại product để có ID ảnh (nếu ảnh được save cùng lúc)
        Product reloadedProduct = productRepository.findById(savedProduct.getId()).get();
        return productMapper.toProductDetailResponse(reloadedProduct);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateMyProduct(Authentication authentication, Long productId, ProductRequest request) {
        User farmer = getUserFromAuthentication(authentication);
        Product product = findMyProductById(productId, farmer.getId()); // Kiểm tra ownership

        // Logic kiểm tra trạng thái sản phẩm trước khi cho cập nhật (ví dụ)
        if (product.getStatus() == ProductStatus.PENDING_APPROVAL || product.getStatus() == ProductStatus.REJECTED) {
            throw new BadRequestException("Cannot update product while it is pending approval or rejected.");
        }
        if (product.getStatus() == ProductStatus.PUBLISHED && request.getStatus() != ProductStatus.UNPUBLISHED && request.getStatus() != ProductStatus.PUBLISHED) {
            // Nếu đã publish, farmer chỉ có thể chuyển sang UNPUBLISHED
            log.warn("Farmer {} trying to change status of published product {} to {}", farmer.getId(), productId, request.getStatus());
            // Giữ nguyên status hoặc set là UNPUBLISHED tùy logic
            // request.setStatus(product.getStatus()); // Ví dụ: giữ nguyên
            request.setStatus(ProductStatus.UNPUBLISHED); // Ví dụ: Chuyển thành ẩn
        } else if (product.getStatus() != ProductStatus.PUBLISHED && request.getStatus() != ProductStatus.DRAFT && request.getStatus() != ProductStatus.UNPUBLISHED) {
            // Nếu chưa publish, farmer chỉ được set DRAFT hoặc UNPUBLISHED
            log.warn("Farmer {} trying to set invalid status {} for non-published product {}", farmer.getId(), request.getStatus(), productId);
            request.setStatus(product.getStatus()); // Giữ nguyên status cũ
        }


        // Cập nhật slug nếu tên thay đổi
        String newSlug = product.getSlug();
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(product.getName())) {
            newSlug = generateUniqueSlug(request.getName(), productId);
        }

        // Cập nhật category nếu có thay đổi
        if (request.getCategoryId() != null && !request.getCategoryId().equals(product.getCategory().getId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        }

        // Cập nhật các trường khác
        productMapper.updateProductFromRequest(request, product);
        product.setSlug(newSlug); // Gán slug mới (có thể là slug cũ nếu tên không đổi)

        // Xử lý cập nhật ảnh thông minh
        updateProductImagesFromRequest(product, request.getImages()); // Gọi helper mới

        // Xử lý cập nhật ảnh và bậc giá
//        updateProductImages(product, request.getImageUrls());
        updatePricingTiers(product, request.getIsB2bAvailable(), request.getPricingTiers());

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated with id: {} by farmer: {}", updatedProduct.getId(), farmer.getId());
        return productMapper.toProductDetailResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteMyProduct(Authentication authentication, Long productId) {
        User farmer = getUserFromAuthentication(authentication);
        Product product = findMyProductById(productId, farmer.getId()); // Kiểm tra ownership

        // Thực hiện soft delete
        productRepository.delete(product);
        log.info("Product soft deleted with id: {} by farmer: {}", productId, farmer.getId());
    }

    // --- Public Methods ---


    //cái này là cái searchPublicProductsWithFullText không hard code
//    @Override
//    @Transactional(readOnly = true)
//    public Page<ProductSummaryResponse> searchPublicProducts(String keyword, Integer categoryId, String provinceCode, Pageable pageable) {
//        // Bỏ Specification nếu dùng Full-Text Search qua native query
//        /*
//        Specification<Product> spec = Specification.where(ProductSpecifications.isPublished())
//                                             .and(ProductSpecifications.hasKeyword(keyword)) // Bỏ cái này nếu dùng MATCH AGAINST
//                                             .and(ProductSpecifications.inCategory(categoryId))
//                                             .and(ProductSpecifications.inProvince(provinceCode));
//        return productRepository.findAll(spec, pageable)
//                .map(productMapper::toProductSummaryResponse);
//        */
//
//        // Gọi phương thức native query mới
//        // Xử lý keyword: MySQL Full-Text thường bỏ qua từ quá ngắn hoặc stop words
//        // Có thể cần chuẩn hóa keyword trước khi truyền vào
//        String searchKeyword = StringUtils.hasText(keyword) ? keyword : null;
//
//        return productRepository.searchPublicProductsWithFullText(searchKeyword, categoryId, provinceCode, pageable)
//                .map(productMapper::toProductSummaryResponse);
//    }



    //cái này là cái searchPublicProductsWithFullText có  hard code
//    @Override
//    @Transactional(readOnly = true)
//    public Page<ProductSummaryResponse> searchPublicProducts(String keyword, Integer categoryId, String provinceCode, Pageable pageable) {
//        String searchKeyword = StringUtils.hasText(keyword) ? keyword : null;
//
//        // *** Tạo Pageable mới không có thông tin sort ***
//        // Spring Data JPA sẽ không cố gắng thêm ORDER BY từ Pageable nữa
//        // vì Native Query đã có ORDER BY riêng.
//        Pageable pageableWithoutSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
//
//        // Gọi phương thức native query với Pageable không có sort
//        Page<Product> productPage = productRepository.searchPublicProductsWithFullText(
//                searchKeyword, categoryId, provinceCode, pageableWithoutSort // *** Truyền Pageable mới ***
//        );
//
//        // Map Page<Product> sang Page<ProductSummaryResponse>
//        return productPage.map(productMapper::toProductSummaryResponse);
//    }

    //cái này là tìm kiếm like % .... %
    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> searchPublicProducts(String keyword, Integer categoryId, String provinceCode, Pageable pageable) {
        // *** Quay lại sử dụng Specification API ***
        Specification<Product> spec = Specification.where(ProductSpecifications.isPublished()) // Bắt đầu với điều kiện published
                // .and(ProductSpecifications.isNotDeleted()) // Thêm nếu không dùng @Where
                .and(ProductSpecifications.hasKeyword(keyword))
                .and(ProductSpecifications.inCategory(categoryId))
                .and(ProductSpecifications.inProvince(provinceCode));

        // findAll với Specification và Pageable (bao gồm sort từ client)
        Page<Product> productPage = productRepository.findAll(spec, pageable);

        // Map kết quả sang DTO
        return productPage.map(productMapper::toProductSummaryResponse);
    }


    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published Product", "slug", slug));
        ProductDetailResponse response = productMapper.toProductDetailResponse(product);
        // Lấy và gán sản phẩm liên quan
        response.setRelatedProducts(findRelatedProducts(product));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicProductById(Long id) {
        Product product = productRepository.findByIdAndStatus(id, ProductStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published Product", "id", id));
        ProductDetailResponse response = productMapper.toProductDetailResponse(product);
        // Lấy và gán sản phẩm liên quan
        response.setRelatedProducts(findRelatedProducts(product));
        return response;
    }

    // ===== IMPLEMENT PHƯƠNG THỨC MỚI =====
    @Override
    @Transactional(readOnly = true) // Giao dịch chỉ đọc
    public Page<ProductSummaryResponse> getPublicProductsByFarmerId(Long farmerId, Pageable pageable) {
        log.debug("Fetching public products for farmer ID: {} with pageable: {}", farmerId, pageable);

        // (Tùy chọn) Kiểm tra xem farmerId có tồn tại không
        if (!userRepository.existsByIdAndRoles_Name(farmerId, RoleType.ROLE_FARMER)) { // Cần thêm phương thức này vào UserRepository hoặc kiểm tra đơn giản bằng existsById
            log.warn("Attempted to fetch products for non-existent or non-farmer user ID: {}", farmerId);
            // Có thể ném ResourceNotFoundException hoặc trả về trang rỗng tùy logic
            // throw new ResourceNotFoundException("Farmer", "id", farmerId);
            return Page.empty(pageable); // Trả về trang rỗng
        }


        // Gọi phương thức repository đã thêm (findByFarmerIdAndStatus)
        Page<Product> productPage = productRepository.findByFarmerIdAndStatus(
                farmerId,
                ProductStatus.PUBLISHED, // Chỉ lấy sản phẩm đã PUBLISHED
                pageable
        );

        // Map kết quả Page<Product> sang Page<ProductSummaryResponse>
        return productPage.map(productMapper::toProductSummaryResponse);
    }
    // =====================================


    // --- Admin Methods ---

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAllProductsForAdmin(String status, Integer categoryId, Long farmerId, Pageable pageable) {
        // Lọc cả sản phẩm chưa publish, đã xóa mềm nếu cần (tùy yêu cầu)
        // Hiện tại đang dùng repo mặc định (lọc is_deleted=false)
        Specification<Product> spec = Specification.where(ProductSpecifications.hasStatus(status)) // Tạo spec này
                .and(ProductSpecifications.inCategory(categoryId))
                .and(ProductSpecifications.byFarmer(farmerId)); // Tạo spec này

        return productRepository.findAll(spec, pageable)
                .map(productMapper::toProductSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductByIdForAdmin(Long productId) {
        Product product = findProductByIdForAdmin(productId);
        ProductDetailResponse response = productMapper.toProductDetailResponse(product);
        // Admin có thể không cần xem sản phẩm liên quan
        // response.setRelatedProducts(findRelatedProducts(product));
        return response;
    }

    @Override
    @Transactional
    public ProductDetailResponse approveProduct(Long productId) {
        Product product = findProductByIdForAdmin(productId); // Helper method lấy sp
        if (product.getStatus() == ProductStatus.PENDING_APPROVAL || product.getStatus() == ProductStatus.REJECTED || product.getStatus() == ProductStatus.DRAFT) {
            product.setStatus(ProductStatus.PUBLISHED);
            product.setRejectReason(null); // Xóa lý do từ chối nếu có
            Product savedProduct = productRepository.save(product);
            log.info("Product {} approved by admin.", productId);
            // Gửi thông báo cho farmer (nếu có NotificationService)
            return productMapper.toProductDetailResponse(savedProduct);
        } else {
            log.warn("Admin tried to approve product {} which is already in status {}", productId, product.getStatus());
            throw new BadRequestException("Product cannot be approved from its current status: " + product.getStatus());
        }
    }

    @Override
    @Transactional
    public ProductDetailResponse rejectProduct(Long productId, String reason) { // Thêm reason nếu cần lưu lại
        Product product = findProductByIdForAdmin(productId);
        if (product.getStatus() == ProductStatus.PENDING_APPROVAL || product.getStatus() == ProductStatus.DRAFT) {
            product.setStatus(ProductStatus.REJECTED);
            // Lưu lý do từ chối vào đâu đó nếu cần (ví dụ: thêm trường reject_reason vào Product)
            product.setRejectReason(StringUtils.hasText(reason) ? reason : "Rejected without specific reason."); // Lưu lý do
            Product savedProduct = productRepository.save(product);
            log.info("Product {} rejected by admin. Reason: {}", productId, reason);
            // Gửi thông báo cho farmer
            return productMapper.toProductDetailResponse(savedProduct);
        } else {
            log.warn("Admin tried to reject product {} which is already in status {}", productId, product.getStatus());
            throw new BadRequestException("Product cannot be rejected from its current status: " + product.getStatus());
        }
    }

    @Override
    @Transactional
    public void forceDeleteProduct(Long productId) {
        // Cần phương thức tìm kiếm bỏ qua @Where
        // Product product = productRepository.findByIdIncludingDeleted(productId)...
        if (productRepository.existsById(productId)) { // Kiểm tra tồn tại (kể cả đã soft delete)
            productRepository.deleteById(productId); // Xóa vật lý
            log.info("Product {} permanently deleted by admin.", productId);
        } else {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
    }

    // --- Helper Methods ---

    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName();
        // findByEmail đã tự lọc is_deleted=false
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    private Product findMyProductById(Long productId, Long farmerId) {
        // findByIdAndFarmerId đã tự lọc is_deleted=false
        return productRepository.findByIdAndFarmerId(productId, farmerId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId + " for farmer " + farmerId));
    }

    private Product findProductByIdForAdmin(Long productId) {
        // findById đã tự lọc is_deleted=false
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }


    private void validateFarmer(User user) {
        if (!user.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
            throw new AccessDeniedException("User must be a Farmer.");
        }
    }

    private String generateUniqueSlug(String name, Long currentProductId) {
        String slug = slugify.slugify(name);
        int count = 0;
        String uniqueSlug = slug;
        // Kiểm tra xem slug có tồn tại cho sản phẩm khác không
        while (productRepository.existsBySlugAndIdNot(uniqueSlug, currentProductId == null ? -1L : currentProductId)) { // Dùng -1L cho trường hợp tạo mới
            count++;
            uniqueSlug = slug + "-" + count;
        }
        return uniqueSlug;
    }

    // --- Helper mới xử lý ảnh từ DTO ---
    private void updateProductImagesFromRequest(Product product, List<ProductImageRequest> imageRequests) {
        if (imageRequests == null) {
            // Nếu request không có trường images -> không thay đổi ảnh hiện có
            return;
        }

        // Lấy danh sách ảnh hiện tại từ DB (hoặc từ product.getImages() nếu fetch EAGER)
        Map<Long, ProductImage> existingImagesMap = product.getImages().stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        Set<ProductImage> updatedImages = new HashSet<>();
        Set<Long> requestImageIds = new HashSet<>();
        boolean hasDefault = false;

        // Duyệt qua danh sách ảnh từ request
        for (ProductImageRequest imgReq : imageRequests) {
            ProductImage image;
            if (imgReq.getId() != null && existingImagesMap.containsKey(imgReq.getId())) {
                // Nếu là ảnh đã có -> cập nhật thông tin (order, isDefault)
                image = existingImagesMap.get(imgReq.getId());
                productImageMapper.updateProductImageFromRequest(imgReq, image);
                requestImageIds.add(imgReq.getId()); // Đánh dấu ID đã xử lý
            } else {
                // Nếu là ảnh mới (không có ID hoặc ID không tồn tại) -> tạo mới
                // Cần URL thực tế từ FileStorageService nếu dùng upload
                image = productImageMapper.requestToProductImage(imgReq);
                image.setProduct(product); // Gán product
            }
            // Đảm bảo chỉ có 1 ảnh default
            if (imgReq.getIsDefault() != null && imgReq.getIsDefault()) {
                if (hasDefault) {
                    image.setDefault(false); // Chỉ cho phép 1 default
                } else {
                    image.setDefault(true);
                    hasDefault = true;
                }
            } else if (!hasDefault && imageRequests.indexOf(imgReq) == imageRequests.size() - 1) {
                // Nếu duyệt hết mà chưa có default -> set cái cuối cùng là default (hoặc cái đầu tiên)
                image.setDefault(true);
            } else {
                image.setDefault(false); // Các trường hợp còn lại không phải default
            }

            updatedImages.add(image);
        }

        // Xóa những ảnh cũ không có trong request mới
        List<ProductImage> imagesToDelete = existingImagesMap.entrySet().stream()
                .filter(entry -> !requestImageIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        if (!imagesToDelete.isEmpty()) {
            productImageRepository.deleteAll(imagesToDelete);
            // Xóa file vật lý nếu cần: imagesToDelete.forEach(img -> fileStorageService.delete(...));
            log.debug("Deleted {} images for product {}", imagesToDelete.size(), product.getId());
        }

        // Cập nhật lại collection trong product entity
        product.getImages().clear();
        product.getImages().addAll(updatedImages);
    }




    // Helper xử lý cập nhật bậc giá B2B
    private void updatePricingTiers(Product product, Boolean isB2bAvailable, List<ProductPricingTierRequest> tierRequests) {
        if (tierRequests != null) { // Chỉ xử lý nếu có gửi list (kể cả rỗng)
            pricingTierRepository.deleteByProductId(product.getId()); // Xóa bậc giá cũ
            product.getPricingTiers().clear(); // Xóa khỏi collection

            if (Boolean.TRUE.equals(isB2bAvailable) && !CollectionUtils.isEmpty(tierRequests)) {
                Set<ProductPricingTier> tiers = tierRequests.stream()
                        .map(tierReq -> {
                            ProductPricingTier tier = pricingTierMapper.requestToProductPricingTier(tierReq);
                            tier.setProduct(product);
                            return tier;
                        }).collect(Collectors.toSet());
                product.setPricingTiers(tiers); // Thêm vào collection mới
            }
        }
        // Nếu tierRequests là null -> không làm gì cả, giữ nguyên bậc giá cũ
    }

    // --- Helper Method mới để tìm sản phẩm liên quan ---
    private List<ProductSummaryResponse> findRelatedProducts(Product currentProduct) {
        List<Product> related = new ArrayList<>();
        List<Long> excludedIds = new ArrayList<>();
        excludedIds.add(currentProduct.getId()); // Luôn loại trừ chính nó

        // 1. Tìm sản phẩm cùng danh mục
        if (currentProduct.getCategory() != null) {
            Pageable limit = PageRequest.of(0, RELATED_PRODUCTS_LIMIT);
            List<Product> sameCategoryProducts = productRepository.findTopNByCategoryIdAndIdNotAndStatus(
                    currentProduct.getCategory().getId(),
                    currentProduct.getId(),
                    ProductStatus.PUBLISHED,
                    limit
            );
            related.addAll(sameCategoryProducts);
            // Thêm ID của các sản phẩm này vào danh sách loại trừ cho bước sau
            related.forEach(p -> excludedIds.add(p.getId()));
        }

        // 2. Nếu chưa đủ, tìm thêm sản phẩm cùng nông dân
        int remainingLimit = RELATED_PRODUCTS_LIMIT - related.size();
        if (remainingLimit > 0 && currentProduct.getFarmer() != null) {
            Pageable limit = PageRequest.of(0, remainingLimit);
            List<Product> sameFarmerProducts = productRepository.findTopNByFarmerIdAndIdNotInAndStatus(
                    currentProduct.getFarmer().getId(),
                    excludedIds, // Loại trừ sản phẩm hiện tại và sp cùng category đã lấy
                    ProductStatus.PUBLISHED,
                    limit
            );
            related.addAll(sameFarmerProducts);
        }

        // Map kết quả sang DTO Summary
        if (related.isEmpty()) {
            return Collections.emptyList();
        }
        return productMapper.toProductSummaryResponseList(related);
    }
}