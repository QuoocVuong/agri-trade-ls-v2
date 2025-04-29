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

    private final ProductPricingTierMapper productPricingTierMapper;

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
    @Transactional // Đảm bảo tất cả thao tác trong một transaction
    public ProductDetailResponse updateMyProduct(Authentication authentication, Long productId, ProductRequest request) {
        User farmer = getUserFromAuthentication(authentication); // 1. Lấy thông tin Farmer
        Product existingProduct = findMyProductById(productId, farmer.getId()); // 2. Tìm sản phẩm và kiểm tra ownership

        ProductStatus previousStatus = existingProduct.getStatus(); // Lưu trạng thái cũ
        boolean wasPublished = previousStatus == ProductStatus.PUBLISHED;

        // 3. Cập nhật các trường cơ bản (trừ collection, category, slug, status)
        productMapper.updateProductFromRequest(request, existingProduct);

        // 4. Cập nhật Category nếu có thay đổi
        updateCategoryIfNeeded(request, existingProduct);

        // 5. Cập nhật Slug nếu tên hoặc slug trong request thay đổi
        updateSlugIfNeeded(request, existingProduct);

        // 6. Cập nhật danh sách Ảnh (dùng clear/addAll)
        updateProductImages(existingProduct, request.getImages());

        // 7. Cập nhật danh sách Bậc giá B2B (dùng clear/addAll)
        updatePricingTiers(existingProduct, request.getIsB2bAvailable(), request.getPricingTiers());

        // 8. Xử lý cập nhật Trạng thái sản phẩm
        updateProductStatusForFarmer(request, existingProduct, previousStatus, wasPublished);

        // 9. Lưu sản phẩm và các thay đổi liên quan
        Product savedProduct = productRepository.save(existingProduct);
        log.info("Updated product {} for farmer {}", productId, farmer.getId());

        // 10. Gửi thông báo nếu trạng thái chuyển về PENDING_APPROVAL
        if (savedProduct.getStatus() == ProductStatus.PENDING_APPROVAL && wasPublished) {
            // notificationService.sendProductNeedsReapprovalNotification(savedProduct); // Cần tạo hàm này
            log.info("Product {} requires re-approval after update.", savedProduct.getId());
        }

        // 11. Load lại đầy đủ thông tin để trả về DTO chi tiết
        // Gọi lại getMyProductById để đảm bảo fetch đúng các association
        return getMyProductById(authentication, savedProduct.getId());
    }

    // --- Helper Methods for updateMyProduct ---

    private void updateCategoryIfNeeded(ProductRequest request, Product product) {
        if (request.getCategoryId() != null && !request.getCategoryId().equals(product.getCategory().getId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        }
    }

    private void updateSlugIfNeeded(ProductRequest request, Product product) {
        String currentSlug = product.getSlug();
        String potentialNewSlug = currentSlug; // Giữ slug cũ mặc định

        // Ưu tiên slug do người dùng nhập nếu có và khác slug hiện tại (sau khi slugify)
        if (StringUtils.hasText(request.getSlug())) {
            String requestedSlugified = slugify.slugify(request.getSlug());
            if (!requestedSlugified.equals(currentSlug)) {
                potentialNewSlug = requestedSlugified;
            }
        }
        // Nếu slug không được nhập thủ công VÀ tên sản phẩm thay đổi -> tạo slug mới từ tên
        else if (StringUtils.hasText(request.getName()) && !request.getName().equals(product.getName())) {
            String nameSlugified = slugify.slugify(request.getName());
            if (!nameSlugified.equals(currentSlug)) {
                potentialNewSlug = nameSlugified;
            }
        }

        // Chỉ kiểm tra và cập nhật nếu slug tiềm năng khác slug hiện tại
        if (!potentialNewSlug.equals(currentSlug)) {
            // Kiểm tra trùng lặp với các sản phẩm khác
            if (productRepository.existsBySlugAndIdNot(potentialNewSlug, product.getId())) {
                // Có thể tạo slug duy nhất bằng cách thêm số hoặc ném lỗi yêu cầu người dùng sửa
                // Ví dụ: Ném lỗi
                throw new BadRequestException("Slug '" + potentialNewSlug + "' đã được sử dụng. Vui lòng chọn slug khác hoặc để trống để tự động tạo.");
                // Ví dụ: Tạo slug duy nhất (cần hàm generateUniqueSlug phức tạp hơn)
                // product.setSlug(generateUniqueSlug(potentialNewSlug, product.getId()));
            } else {
                product.setSlug(potentialNewSlug); // Gán slug mới nếu không trùng
            }
        }
        // Nếu slug tiềm năng giống slug hiện tại -> không làm gì cả
    }


    private void updateProductImages(Product product, List<ProductImageRequest> imageRequests) {
        Set<ProductImage> existingImages = product.getImages();
        if (existingImages == null) {
            existingImages = new HashSet<>();
            product.setImages(existingImages);
        }

        Set<ProductImage> requestedImages = new HashSet<>();
        boolean hasDefaultInRequest = false;
        if (imageRequests != null) {
            int order = 0;
            for (ProductImageRequest req : imageRequests) {
                // Logic này giả định FE gửi đầy đủ list ảnh mong muốn cuối cùng
                // và không gửi ID cho ảnh mới, chỉ gửi ID cho ảnh muốn giữ lại/cập nhật
                ProductImage img = productImageMapper.requestToProductImage(req);
                img.setProduct(product);
                img.setDisplayOrder(order++); // Gán lại thứ tự dựa trên request
                img.setDefault(req.getIsDefault() != null && req.getIsDefault()); // Cập nhật isDefault
                if (img.isDefault()) {
                    hasDefaultInRequest = true;
                }
                requestedImages.add(img);
            }
        }

        // Sử dụng clear/addAll với orphanRemoval=true
        existingImages.clear();
        existingImages.addAll(requestedImages);

        // Đảm bảo có ảnh default nếu list không rỗng và chưa có default nào được set từ request
        if (!existingImages.isEmpty() && !hasDefaultInRequest) {
            existingImages.stream().min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                    .ifPresent(img -> img.setDefault(true));
        }
    }

    private void updatePricingTiers(Product product, Boolean isB2bAvailable, List<ProductPricingTierRequest> tierRequests) {
        Set<ProductPricingTier> existingTiers = product.getPricingTiers();
        if (existingTiers == null) {
            existingTiers = new HashSet<>();
            product.setPricingTiers(existingTiers);
        }

        // Xóa hết tier cũ nếu sản phẩm không còn là B2B hoặc request gửi list rỗng
        if (Boolean.FALSE.equals(isB2bAvailable) || (tierRequests != null && tierRequests.isEmpty())) {
            if (!existingTiers.isEmpty()) {
                existingTiers.clear(); // orphanRemoval sẽ xóa khỏi DB
                log.debug("Cleared pricing tiers for product {}", product.getId());
            }
            return; // Kết thúc nếu không cho B2B hoặc list rỗng
        }

        // Chỉ cập nhật nếu tierRequests được gửi (không phải null)
        if (tierRequests != null) {
            Set<ProductPricingTier> requestedTiers = tierRequests.stream()
                    .map(req -> {
                        ProductPricingTier tier = productPricingTierMapper.requestToProductPricingTier(req);
                        tier.setProduct(product);
                        return tier;
                    })
                    .collect(Collectors.toSet());

            // Dùng clear/addAll
            existingTiers.clear();
            existingTiers.addAll(requestedTiers);
            log.debug("Updated pricing tiers for product {}", product.getId());
        }
        // Nếu tierRequests là null -> không làm gì, giữ nguyên tier cũ
    }

    private void updateProductStatusForFarmer(ProductRequest request, Product product, ProductStatus previousStatus, boolean wasPublished) {
        ProductStatus requestedStatus = request.getStatus();

        // Chỉ cho phép Farmer set DRAFT hoặc UNPUBLISHED
        if (requestedStatus == ProductStatus.DRAFT || requestedStatus == ProductStatus.UNPUBLISHED) {
            // Nếu trước đó đã public/pending, giờ chỉ có thể là UNPUBLISHED
            if (wasPublished || previousStatus == ProductStatus.PENDING_APPROVAL) {
                product.setStatus(ProductStatus.UNPUBLISHED);
            } else {
                product.setStatus(requestedStatus); // Cho phép đổi giữa Draft/Unpublished
            }
        } else if (requestedStatus != null && requestedStatus != previousStatus) {
            // Nếu Farmer cố set trạng thái khác (PUBLISHED, REJECTED, PENDING) -> bỏ qua, giữ trạng thái cũ
            log.warn("Farmer attempted to set invalid status {} for product {}. Keeping status {}.",
                    requestedStatus, product.getId(), previousStatus);
            product.setStatus(previousStatus); // Giữ nguyên trạng thái cũ
        }
        // Không cần else vì nếu request.status là null hoặc giống previousStatus thì không đổi

        // Logic chuyển về PENDING khi sửa sản phẩm PUBLISHED
        boolean significantChange = wasPublished && hasSignificantChanges(product, request); // Kiểm tra thay đổi
        // Chỉ chuyển về PENDING nếu trạng thái hiện tại *không phải* là UNPUBLISHED (do farmer chủ động ẩn)
        if (significantChange && product.getStatus() != ProductStatus.UNPUBLISHED) {
            log.info("Significant changes detected for published product {}. Setting status to PENDING_APPROVAL.", product.getId());
            product.setStatus(ProductStatus.PENDING_APPROVAL);
        }
    }

    private boolean hasSignificantChanges(Product existingProduct, ProductRequest request) {
        // So sánh các trường quan trọng
        if (request.getName() != null && !Objects.equals(existingProduct.getName(), request.getName())) return true;
        if (request.getDescription() != null && !Objects.equals(existingProduct.getDescription(), request.getDescription())) return true;
        if (request.getPrice() != null && (existingProduct.getPrice() == null || existingProduct.getPrice().compareTo(request.getPrice()) != 0)) return true;
        if (request.getCategoryId() != null && !Objects.equals(existingProduct.getCategory().getId(), request.getCategoryId())) return true;
        if (request.getUnit() != null && !Objects.equals(existingProduct.getUnit(), request.getUnit())) return true;
        // Thêm kiểm tra cho các trường B2B nếu cần thiết
        if (!Objects.equals(existingProduct.isB2bAvailable(), request.getIsB2bAvailable())) return true;
        if (Boolean.TRUE.equals(request.getIsB2bAvailable())) {
            if (request.getB2bUnit() != null && !Objects.equals(existingProduct.getB2bUnit(), request.getB2bUnit())) return true;
            if (request.getMinB2bQuantity() != null && !Objects.equals(existingProduct.getMinB2bQuantity(), request.getMinB2bQuantity())) return true;
            if (request.getB2bBasePrice() != null && (existingProduct.getB2bBasePrice() == null || existingProduct.getB2bBasePrice().compareTo(request.getB2bBasePrice()) != 0)) return true;
            // Có thể kiểm tra cả thay đổi trong pricingTiers và images nếu muốn chặt chẽ hơn
        }
        return false;
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
    // *** SỬA LẠI: Dùng Specification API (LIKE Search) ***
    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> searchPublicProducts(String keyword, Integer categoryId, String provinceCode, Pageable pageable) {
        log.debug("Searching public products with keyword: '{}', categoryId: {}, provinceCode: {}, pageable: {}", keyword, categoryId, provinceCode, pageable);
        Specification<Product> spec = Specification.where(ProductSpecifications.isPublished())
                .and(ProductSpecifications.hasKeyword(keyword))
                .and(ProductSpecifications.inCategory(categoryId))
                .and(ProductSpecifications.inProvince(provinceCode));
        // findAll với Specification và Pageable (đã bao gồm sort)
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        // Map kết quả sang DTO
        return productPage.map(productMapper::toProductSummaryResponse);
    }
    // *************************************************


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




//    // Helper xử lý cập nhật bậc giá B2B
//    private void updatePricingTiers(Product product, Boolean isB2bAvailable, List<ProductPricingTierRequest> tierRequests) {
//        if (tierRequests != null) { // Chỉ xử lý nếu có gửi list (kể cả rỗng)
//            pricingTierRepository.deleteByProductId(product.getId()); // Xóa bậc giá cũ
//            product.getPricingTiers().clear(); // Xóa khỏi collection
//
//            if (Boolean.TRUE.equals(isB2bAvailable) && !CollectionUtils.isEmpty(tierRequests)) {
//                Set<ProductPricingTier> tiers = tierRequests.stream()
//                        .map(tierReq -> {
//                            ProductPricingTier tier = pricingTierMapper.requestToProductPricingTier(tierReq);
//                            tier.setProduct(product);
//                            return tier;
//                        }).collect(Collectors.toSet());
//                product.setPricingTiers(tiers); // Thêm vào collection mới
//            }
//        }
//        // Nếu tierRequests là null -> không làm gì cả, giữ nguyên bậc giá cũ
//    }

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