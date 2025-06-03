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
import com.yourcompany.agritrade.catalog.mapper.ProductPricingTierMapper;
import com.yourcompany.agritrade.catalog.repository.*;
import com.yourcompany.agritrade.catalog.repository.specification.ProductSpecifications;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization; // SỬA LẠI IMPORT NÀY
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

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
  private final ReviewService reviewService;
  private static final int RELATED_PRODUCTS_LIMIT = 4; // Số lượng sản phẩm liên quan muốn hiển thị

  private final ProductPricingTierMapper productPricingTierMapper;

  private final NotificationService notificationService;
  private final EmailService emailService;

  // ****** KHAI BÁO VÀ INJECT publicBaseUrl ******
  @Value("${firebase.storage.public-base-url:#{null}}") // Inject giá trị từ application.yml
  private String publicBaseUrl;

  // ********************************************

  // --- Farmer Methods ---

  @Override
  @Transactional(readOnly = true)
  public Page<ProductSummaryResponse> getMyProducts(
      Authentication authentication, String keyword, ProductStatus status, Pageable pageable) {
    User farmer = getUserFromAuthentication(authentication);
    // findByFarmerId đã tự lọc is_deleted=false nhờ @Where
    //        return productRepository.findByFarmerId(farmer.getId(), pageable)
    //                .map(productMapper::toProductSummaryResponse);

    // Xây dựng Specification để lọc
    Specification<Product> spec =
        Specification.where(
            ProductSpecifications.byFarmer(farmer.getId())); // Luôn lọc theo farmerId

    if (StringUtils.hasText(keyword)) {
      spec = spec.and(ProductSpecifications.hasKeyword(keyword)); // Tái sử dụng specification đã có
    }
    if (status != null) {
      spec =
          spec.and(
              ProductSpecifications.hasStatus(
                  status.name())); // Tái sử dụng specification, truyền tên Enum
    }
    // Không cần fetchFarmerAndProfile ở đây vì đã lọc theo farmer.id
    // Có thể fetch category nếu ProductSummaryResponse cần tên category
    // spec = spec.and(ProductSpecifications.fetchCategory());

    Page<Product> productPage = productRepository.findAll(spec, pageable);

    // QUAN TRỌNG: Gọi populateImageUrls cho mỗi sản phẩm TRƯỚC KHI MAP
    productPage
        .getContent()
        .forEach(this::populateImageUrls); // Hoặc productPage.forEach(this::populateImageUrls);

    return productPage.map(productMapper::toProductSummaryResponse);
  }

  // Phương thức helper để điền imageUrls (đã có từ trước)
  private void populateImageUrls(Product product) {
    if (product != null && product.getImages() != null && !product.getImages().isEmpty()) {
      for (ProductImage image : product.getImages()) {
        if (StringUtils.hasText(image.getBlobPath())) {
          image.setImageUrl(fileStorageService.getFileUrl(image.getBlobPath()));
        }
      }
    }
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
  public ProductDetailResponse createMyProduct(
      Authentication authentication, ProductRequest request) {
    User farmer = getUserFromAuthentication(authentication);
    validateFarmer(farmer); // Kiểm tra vai trò và profile

    FarmerProfile farmerProfile =
        farmerProfileRepository
            .findById(farmer.getId())
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Farmer profile not found. Please complete your profile."));

    // Kiểm tra trạng thái duyệt của Farmer
    if (farmerProfile.getVerificationStatus() != VerificationStatus.VERIFIED) {
      throw new BadRequestException(
          "Your farmer profile is not verified yet. Cannot create products.");
    }

    Category category =
        categoryRepository
            .findById(request.getCategoryId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

    String slug = generateUniqueSlug(request.getName(), null); // Tạo slug unique

    Product product = productMapper.requestToProduct(request);

    // ***** THÊM ĐOẠN NÀY ĐỂ ĐẢM BẢO COLLECTIONS KHÔNG NULL *****
    if (product.getImages() == null) {
      product.setImages(new HashSet<>());
    }
    if (product.getPricingTiers() == null) { // Làm tương tự cho các collection khác nếu cần
      product.setPricingTiers(new HashSet<>());
    }
    // ***********************************************************

    // ***** Đảm bảo các giá trị mặc định cho các trường NOT NULL *****
    if (product.getAverageRating() == null) { // Mặc dù bạn đã có = 0.0f trong entity
      product.setAverageRating(0.0f);
    }
    if (product.getRatingCount() == null) { // Giả sử ratingCount cũng là NOT NULL
      product.setRatingCount(0);
    }
    if (product.getFavoriteCount() == null) { // Giả sử favoriteCount cũng là NOT NULL
      product.setFavoriteCount(0);
    }
// isDeleted đã được khởi tạo là false
// status đã được khởi tạo
// stockQuantity đã được khởi tạo là 0
// B2bEnabled đã được khởi tạo là false
// ... kiểm tra các trường NOT NULL khác nếu cần ...
// ************************************************************

    product.setSlug(slug);
    product.setFarmer(farmer);
    product.setCategory(category);
    product.setProvinceCode(farmerProfile.getProvinceCode());
    product.setStatus(ProductStatus.PENDING_APPROVAL); // Luôn bắt đầu là DRAFT

    // Xử lý ảnh thông minh hơn (từ request DTO)
    updateProductImagesFromRequest(product, request.getImages()); // Gọi helper mới

    // Xử lý ảnh và bậc giá
    //        updateProductImages(product, request.getImageUrls());
    updatePricingTiers(product, request.isB2bEnabled(), request.getPricingTiers());

    Product savedProduct = productRepository.save(product);
    log.info("Product created with id: {} by farmer: {}", savedProduct.getId(), farmer.getId());
    // Cần load lại product để có ID ảnh (nếu ảnh được save cùng lúc)
    Product reloadedProduct = productRepository.findByIdWithDetails(savedProduct.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", savedProduct.getId()));
    return productMapper.toProductDetailResponse(reloadedProduct);
  }

  @Override
  @Transactional // Đảm bảo tất cả thao tác trong một transaction
  public ProductDetailResponse updateMyProduct(
      Authentication authentication, Long productId, ProductRequest request) {
    User farmer = getUserFromAuthentication(authentication); // 1. Lấy thông tin Farmer
    Product existingProduct =
        findMyProductById(productId, farmer.getId()); // 2. Tìm sản phẩm và kiểm tra ownership

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
    updatePricingTiers(existingProduct, request.isB2bEnabled(), request.getPricingTiers());

    // 8. Xử lý cập nhật Trạng thái sản phẩm
    updateProductStatusForFarmer(request, existingProduct, previousStatus, wasPublished);

    // 9. Lưu sản phẩm và các thay đổi liên quan
    Product savedProduct = productRepository.save(existingProduct);
    log.info("Updated product {} for farmer {}", productId, farmer.getId());

    // 10. Gửi thông báo nếu trạng thái chuyển về PENDING_APPROVAL
    if (savedProduct.getStatus() == ProductStatus.PENDING_APPROVAL && wasPublished) {
      // notificationService.sendProductNeedsReapprovalNotification(savedProduct); // Cần tạo hàm
      // này
      log.info("Product {} requires re-approval after update.", savedProduct.getId());
    }

    // 11. Load lại đầy đủ thông tin để trả về DTO chi tiết
    // Gọi lại getMyProductById để đảm bảo fetch đúng các association
    return getMyProductById(authentication, savedProduct.getId());
  }

  // --- Helper Methods for updateMyProduct ---

  private void updateCategoryIfNeeded(ProductRequest request, Product product) {
    if (request.getCategoryId() != null
        && !request.getCategoryId().equals(product.getCategory().getId())) {
      Category category =
          categoryRepository
              .findById(request.getCategoryId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
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
    else if (StringUtils.hasText(request.getName())
        && !request.getName().equals(product.getName())) {
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
        throw new BadRequestException(
            "Slug '"
                + potentialNewSlug
                + "' đã được sử dụng. Vui lòng chọn slug khác hoặc để trống để tự động tạo.");
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
      existingImages.stream()
          .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
          .ifPresent(img -> img.setDefault(true));
    }
  }

  private void updatePricingTiers(
      Product product, Boolean isB2bAvailable, List<ProductPricingTierRequest> tierRequests) {
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
      // Nếu không phải B2B thì không cần làm gì thêm với tier
      if (Boolean.FALSE.equals(isB2bAvailable)) return; // Chỉ return nếu isB2bAvailable là FALSE
    }

    // Chỉ cập nhật nếu tierRequests được gửi (không phải null)
    if (tierRequests != null) {
      Set<ProductPricingTier> requestedTiers =
          tierRequests.stream()
              .map(
                  req -> {
                    ProductPricingTier tier =
                        productPricingTierMapper.requestToProductPricingTier(req);
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

  private void updateProductStatusForFarmer(
      ProductRequest request, Product product, ProductStatus previousStatus, boolean wasPublished) {
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
      // Nếu Farmer cố set trạng thái khác (PUBLISHED, REJECTED, PENDING) -> bỏ qua, giữ trạng thái
      // cũ
      log.warn(
          "Farmer attempted to set invalid status {} for product {}. Keeping status {}.",
          requestedStatus,
          product.getId(),
          previousStatus);
      product.setStatus(previousStatus); // Giữ nguyên trạng thái cũ
    }
    // Không cần else vì nếu request.status là null hoặc giống previousStatus thì không đổi

    // Logic chuyển về PENDING khi sửa sản phẩm PUBLISHED
    boolean significantChange =
        wasPublished && hasSignificantChanges(product, request); // Kiểm tra thay đổi
    // Chỉ chuyển về PENDING nếu trạng thái hiện tại *không phải* là UNPUBLISHED (do farmer chủ động
    // ẩn)
    if (significantChange && product.getStatus() != ProductStatus.UNPUBLISHED) {
      log.info(
          "Significant changes detected for published product {}. Setting status to PENDING_APPROVAL.",
          product.getId());
      product.setStatus(ProductStatus.PENDING_APPROVAL);
    }
  }

  private boolean hasSignificantChanges(Product existingProduct, ProductRequest request) {
    // So sánh các trường quan trọng
    if (request.getName() != null && !Objects.equals(existingProduct.getName(), request.getName()))
      return true;
    if (request.getDescription() != null
        && !Objects.equals(existingProduct.getDescription(), request.getDescription())) return true;
    if (request.getPrice() != null
        && (existingProduct.getPrice() == null
            || existingProduct.getPrice().compareTo(request.getPrice()) != 0)) return true;
    if (request.getCategoryId() != null
        && !Objects.equals(existingProduct.getCategory().getId(), request.getCategoryId()))
      return true;
    if (request.getUnit() != null && !Objects.equals(existingProduct.getUnit(), request.getUnit()))
      return true;
    // Thêm kiểm tra cho các trường B2B nếu cần thiết
    if (!Objects.equals(existingProduct.isB2bEnabled(), request.isB2bEnabled())) return true;
    if (Boolean.TRUE.equals(request.isB2bEnabled())) {
      if (request.getB2bUnit() != null
          && !Objects.equals(existingProduct.getB2bUnit(), request.getB2bUnit())) return true;
      if (request.getMinB2bQuantity() != null
          && !Objects.equals(existingProduct.getMinB2bQuantity(), request.getMinB2bQuantity()))
        return true;
      if (request.getB2bBasePrice() != null
          && (existingProduct.getB2bBasePrice() == null
              || existingProduct.getB2bBasePrice().compareTo(request.getB2bBasePrice()) != 0))
        return true;
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

  // cái này là cái searchPublicProductsWithFullText không hard code
  //    @Override
  //    @Transactional(readOnly = true)
  //    public Page<ProductSummaryResponse> searchPublicProducts(String keyword, Integer categoryId,
  // String provinceCode, Pageable pageable) {
  //        // Bỏ Specification nếu dùng Full-Text Search qua native query
  //        /*
  //        Specification<Product> spec = Specification.where(ProductSpecifications.isPublished())
  //                                             .and(ProductSpecifications.hasKeyword(keyword)) //
  // Bỏ cái này nếu dùng MATCH AGAINST
  //                                             .and(ProductSpecifications.inCategory(categoryId))
  //
  // .and(ProductSpecifications.inProvince(provinceCode));
  //        return productRepository.findAll(spec, pageable)
  //                .map(productMapper::toProductSummaryResponse);
  //        */
  //
  //        // Gọi phương thức native query mới
  //        // Xử lý keyword: MySQL Full-Text thường bỏ qua từ quá ngắn hoặc stop words
  //        // Có thể cần chuẩn hóa keyword trước khi truyền vào
  //        String searchKeyword = StringUtils.hasText(keyword) ? keyword : null;
  //
  //        return productRepository.searchPublicProductsWithFullText(searchKeyword, categoryId,
  // provinceCode, pageable)
  //                .map(productMapper::toProductSummaryResponse);
  //    }

  // cái này là cái searchPublicProductsWithFullText có  hard code
  //    @Override
  //    @Transactional(readOnly = true)
  //    public Page<ProductSummaryResponse> searchPublicProducts(String keyword, Integer categoryId,
  // String provinceCode, Pageable pageable) {
  //        String searchKeyword = StringUtils.hasText(keyword) ? keyword : null;
  //
  //        // *** Tạo Pageable mới không có thông tin sort ***
  //        // Spring Data JPA sẽ không cố gắng thêm ORDER BY từ Pageable nữa
  //        // vì Native Query đã có ORDER BY riêng.
  //        Pageable pageableWithoutSort = PageRequest.of(pageable.getPageNumber(),
  // pageable.getPageSize());
  //
  //        // Gọi phương thức native query với Pageable không có sort
  //        Page<Product> productPage = productRepository.searchPublicProductsWithFullText(
  //                searchKeyword, categoryId, provinceCode, pageableWithoutSort // *** Truyền
  // Pageable mới ***
  //        );
  //
  //        // Map Page<Product> sang Page<ProductSummaryResponse>
  //        return productPage.map(productMapper::toProductSummaryResponse);
  //    }

  // cái này là tìm kiếm like % .... %
  // *** SỬA LẠI: Dùng Specification API (LIKE Search) ***
  @Override
  @Transactional(readOnly = true)
  public Page<ProductSummaryResponse> searchPublicProducts(
      String keyword,
      Integer categoryId,
      String provinceCode,
      Double minPrice,
      Double maxPrice,
      Integer minRating,
      Pageable pageable) {
    log.debug(
        "Searching public products with keyword: '{}', categoryId: {}, provinceCode: {}, minPrice: {}, maxPrice: {}, minRating: {}, pageable: {}",
        keyword,
        categoryId,
        provinceCode,
        minPrice,
        maxPrice,
        minRating,
        pageable);
    Specification<Product> spec =
        Specification.where(ProductSpecifications.isPublished())
            .and(ProductSpecifications.hasKeyword(keyword))
            .and(ProductSpecifications.inCategory(categoryId))
            .and(ProductSpecifications.inProvince(provinceCode))
            .and(ProductSpecifications.fetchFarmerAndProfile());

    // Thêm các điều kiện lọc mới vào Specification
    if (minPrice != null) {
      spec = spec.and(ProductSpecifications.hasMinPrice(BigDecimal.valueOf(minPrice)));
    }
    if (maxPrice != null) {
      spec = spec.and(ProductSpecifications.hasMaxPrice(BigDecimal.valueOf(maxPrice)));
    }
    // Chỉ lọc theo minRating nếu nó > 0
    if (minRating != null && minRating > 0) {
      spec =
          spec.and(
              ProductSpecifications.hasMinRating(
                  minRating.doubleValue())); // Chuyển sang double cho averageRating
    }

    // findAll với Specification và Pageable (đã bao gồm sort)
    Page<Product> productPage = productRepository.findAll(spec, pageable);
    // Map kết quả sang DTO
    return productPage.map(productMapper::toProductSummaryResponse);
  }

  // *************************************************

  @Override
  @Transactional(readOnly = true)
  public ProductDetailResponse getPublicProductBySlug(String slug) {

    // Cần đảm bảo fetch đủ thông tin cho trang chi tiết nữa
    // Cách 1: Tạo phương thức repo riêng với @Query và JOIN FETCH
    // Cách 2: Dùng Specification tương tự như searchPublicProducts
    Specification<Product> spec =
        Specification.where(ProductSpecifications.fetchFarmerAndProfile()) // Fetch farmer/profile
            .and((root, query, cb) -> cb.equal(root.get("slug"), slug))
            .and((root, query, cb) -> cb.equal(root.get("status"), ProductStatus.PUBLISHED));

    Product product =
        productRepository
            .findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("Published Product", "slug", slug));
    ProductDetailResponse response = productMapper.toProductDetailResponse(product);
    // Lấy và gán sản phẩm liên quan
    response.setRelatedProducts(findRelatedProducts(product));

    // *** LẤY VÀ GÁN ĐÁNH GIÁ CHO SẢN PHẨM ***
    // Quyết định có phân trang ở đây không, hoặc lấy một số lượng nhất định
    Pageable reviewPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")); // Ví dụ: lấy 5 review mới nhất
    Page<ReviewResponse> productReviewsPage = reviewService.getApprovedReviewsByProduct(product.getId(), reviewPageable);
    if (productReviewsPage != null) {
      response.setReviews(productReviewsPage.getContent());
    } else {
      response.setReviews(Collections.emptyList());
    }
    // *****************************************

    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public ProductDetailResponse getPublicProductById(Long id) {
    Specification<Product> spec =
        Specification.where(ProductSpecifications.fetchFarmerAndProfile())
            .and((root, query, cb) -> cb.equal(root.get("id"), id))
            .and((root, query, cb) -> cb.equal(root.get("status"), ProductStatus.PUBLISHED));

    Product product =
        productRepository
            .findOne(spec)
            .orElseThrow(() -> new ResourceNotFoundException("Published Product", "id", id));

    ProductDetailResponse response = productMapper.toProductDetailResponse(product);
    response.setRelatedProducts(findRelatedProducts(product));

    // *** LẤY VÀ GÁN ĐÁNH GIÁ CHO SẢN PHẨM ***
    Pageable reviewPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<ReviewResponse> productReviewsPage = reviewService.getApprovedReviewsByProduct(product.getId(), reviewPageable);
    if (productReviewsPage != null) {
      response.setReviews(productReviewsPage.getContent());
    } else {
      response.setReviews(Collections.emptyList());
    }
    // *****************************************

    return response;
  }

  // ===== IMPLEMENT PHƯƠNG THỨC MỚI =====
  @Override
  @Transactional(readOnly = true) // Giao dịch chỉ đọc
  public Page<ProductSummaryResponse> getPublicProductsByFarmerId(
      Long farmerId, Pageable pageable) {
    log.debug("Fetching public products for farmer ID: {} with pageable: {}", farmerId, pageable);

    // (Tùy chọn) Kiểm tra xem farmerId có tồn tại không
    if (!userRepository.existsByIdAndRoles_Name(
        farmerId,
        RoleType
            .ROLE_FARMER)) { // Cần thêm phương thức này vào UserRepository hoặc kiểm tra đơn giản
      // bằng existsById
      log.warn("Attempted to fetch products for non-existent or non-farmer user ID: {}", farmerId);
      // Có thể ném ResourceNotFoundException hoặc trả về trang rỗng tùy logic
      // throw new ResourceNotFoundException("Farmer", "id", farmerId);
      return Page.empty(pageable); // Trả về trang rỗng
    }

    // Gọi phương thức repository đã thêm (findByFarmerIdAndStatus)
    Page<Product> productPage =
        productRepository.findByFarmerIdAndStatus(
            farmerId,
            ProductStatus.PUBLISHED, // Chỉ lấy sản phẩm đã PUBLISHED
            pageable);

    // Map kết quả Page<Product> sang Page<ProductSummaryResponse>
    return productPage.map(productMapper::toProductSummaryResponse);
  }

  // =====================================

  // --- Admin Methods ---

  @Override
  @Transactional(readOnly = true)
  public Page<ProductSummaryResponse> getAllProductsForAdmin(
      String keyword, String status, Integer categoryId, Long farmerId, Pageable pageable) {
    // Lọc cả sản phẩm chưa publish, đã xóa mềm nếu cần (tùy yêu cầu)
    // Hiện tại đang dùng repo mặc định (lọc is_deleted=false)
    Specification<Product> spec =
        Specification.where(ProductSpecifications.hasKeyword(keyword))
            .and(ProductSpecifications.hasStatus(status))
            .and(ProductSpecifications.inCategory(categoryId))
            .and(ProductSpecifications.byFarmer(farmerId));
    // Có thể thêm fetch join nếu ProductSummaryResponse cần thêm thông tin
    // spec = spec.and(ProductSpecifications.fetchFarmerAndProfile());
    // spec = spec.and(ProductSpecifications.fetchCategory());

    Page<Product> productPage = productRepository.findAll(spec, pageable);

    // Gọi populateImageUrls nếu ProductSummaryResponse cần thumbnail
    productPage.getContent().forEach(this::populateImageUrls);

    return productPage.map(productMapper::toProductSummaryResponse);
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
    if (product.getStatus() == ProductStatus.PENDING_APPROVAL
        || product.getStatus() == ProductStatus.REJECTED
        || product.getStatus() == ProductStatus.DRAFT) {
      product.setStatus(ProductStatus.PUBLISHED);
      product.setRejectReason(null); // Xóa lý do từ chối nếu có
      Product savedProduct = productRepository.save(product);
      log.info("Product {} approved by admin.", productId);
      // ****** TẢI TRƯỚC THÔNG TIN FARMER ******
      // Cách 1: Load lại User đầy đủ (nếu cần nhiều thông tin)
      User farmer =
          userRepository
              .findById(savedProduct.getFarmer().getId())
              .orElse(null); // Lấy lại farmer từ DB

      // Cách 2: Hoặc chỉ cần email thì có thể lấy trực tiếp nếu không LAZY quá sâu
      // String farmerEmail = savedProduct.getFarmer().getEmail(); // Có thể vẫn lỗi nếu User là
      // proxy

      // ****** GỌI HÀM ASYNC VỚI DỮ LIỆU ĐÃ LOAD ******
      if (farmer != null) {
        notificationService.sendProductApprovedNotification(
            savedProduct, farmer); // Truyền farmer đã load
        emailService.sendProductApprovedEmailToFarmer(
            savedProduct, farmer); // Truyền farmer đã load
      } else {
        log.error(
            "Could not find farmer with ID {} for product {}",
            savedProduct.getFarmer().getId(),
            productId);
      }
      // ********************************************

      // Trả về response - cần load lại đầy đủ thông tin product
      // Nên dùng một phương thức repository có fetch join đầy đủ ở đây
      Product reloadedProduct =
          productRepository
              .findByIdWithDetails(savedProduct.getId()) // Giả sử có hàm này
              .orElse(savedProduct);
      return productMapper.toProductDetailResponse(reloadedProduct);
    } else {
      log.warn(
          "Admin tried to approve product {} which is already in status {}",
          productId,
          product.getStatus());
      throw new BadRequestException(
          "Product cannot be approved from its current status: " + product.getStatus());
    }
  }

  @Override
  @Transactional
  public ProductDetailResponse rejectProduct(Long productId, String reason) {
    Product product = findProductByIdForAdmin(productId);
    if (product.getStatus() == ProductStatus.PENDING_APPROVAL
        || product.getStatus() == ProductStatus.DRAFT) {
      product.setStatus(ProductStatus.REJECTED);
      product.setRejectReason(
          StringUtils.hasText(reason) ? reason : "Rejected without specific reason.");
      Product savedProduct = productRepository.save(product);
      log.info("Product {} rejected by admin. Reason: {}", productId, reason);

      // ****** TẢI TRƯỚC THÔNG TIN FARMER ******
      User farmer = userRepository.findById(savedProduct.getFarmer().getId()).orElse(null);
      // ***************************************

      // ****** GỌI HÀM ASYNC VỚI DỮ LIỆU ĐÃ LOAD ******
      if (farmer != null) {
        notificationService.sendProductRejectedNotification(
            savedProduct, reason, farmer); // Truyền farmer
        emailService.sendProductRejectedEmailToFarmer(
            savedProduct, reason, farmer); // Truyền farmer
      } else {
        log.error(
            "Could not find farmer with ID {} for product {}",
            savedProduct.getFarmer().getId(),
            productId);
      }
      // ********************************************

      // Trả về response - cần load lại đầy đủ
      Product reloadedProduct =
          productRepository
              .findByIdWithDetails(savedProduct.getId()) // Giả sử có hàm này
              .orElse(savedProduct);
      return productMapper.toProductDetailResponse(reloadedProduct);
    } else {
      // ... xử lý lỗi BadRequestException ...
      throw new BadRequestException(
          "Product cannot be rejected from its current status: " + product.getStatus());
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
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  private Product findMyProductById(Long productId, Long farmerId) {
    // findByIdAndFarmerId đã tự lọc is_deleted=false
    return productRepository
        .findByIdAndFarmerId(productId, farmerId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Product", "id", productId + " for farmer " + farmerId));
  }

  private Product findProductByIdForAdmin(Long productId) {
    // findById đã tự lọc is_deleted=false
    return productRepository
        .findById(productId)
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
    while (productRepository.existsBySlugAndIdNot(
        uniqueSlug,
        currentProductId == null ? -1L : currentProductId)) { // Dùng -1L cho trường hợp tạo mới
      count++;
      uniqueSlug = slug + "-" + count;
    }
    return uniqueSlug;
  }

  // --- Helper mới xử lý ảnh từ DTO ---
  private void updateProductImagesFromRequest(Product product, List<ProductImageRequest> imageRequests) {


    if (product.getImages() == null) {
      product.setImages(new HashSet<>());
    }


    if (imageRequests == null) {
      // Nếu request không có trường images, không thay đổi ảnh hiện có
      // Tuy nhiên, nếu logic của bạn là "nếu không gửi gì thì xóa hết", cần xử lý ở đây
      // Ví dụ: nếu imageRequests là null và product.getImages() không rỗng, thì xóa hết ảnh cũ
      // if (!product.getImages().isEmpty()) {
      //     List<String> blobPathsToDeleteAfterCommit = new ArrayList<>();
      //     product.getImages().forEach(img -> {
      //         if (StringUtils.hasText(img.getBlobPath())) {
      //             blobPathsToDeleteAfterCommit.add(img.getBlobPath());
      //         }
      //     });
      //     productImageRepository.deleteAll(product.getImages()); // Xóa entity khỏi DB
      //     product.getImages().clear(); // Xóa khỏi collection trong product
      //     registerBlobsForDeletionAfterCommit(blobPathsToDeleteAfterCommit);
      // }
      return;
    }

    // Lấy danh sách ảnh hiện tại từ DB (hoặc từ product.getImages() nếu fetch EAGER)
    Map<Long, ProductImage> existingImagesMap =
        product.getImages().stream()
            .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

    Set<ProductImage> updatedImages = new HashSet<>();
    Set<Long> requestImageIds = new HashSet<>(); // Lưu ID của các ảnh có trong request (để biết ảnh nào cần xóa)
    boolean hasDefaultInRequest = false;
    boolean hasDefault = false;

    int order = 0;

    // Danh sách các blobPath của ảnh cũ cần xóa sau khi transaction thành công
    List<String> blobPathsToDeleteAfterCommit = new ArrayList<>();

    // Duyệt qua danh sách ảnh từ request
    for (ProductImageRequest imgReq : imageRequests) {
      ProductImage image;
      if (imgReq.getId() != null && existingImagesMap.containsKey(imgReq.getId())) {
        // Nếu là ảnh đã có -> cập nhật thông tin (order, isDefault)
        image = existingImagesMap.get(imgReq.getId());

        String oldBlobPathThisImage = image.getBlobPath();

        // Chỉ cập nhật isDefault và displayOrder, blobPath nếu nó thay đổi
        image.setDefault(imgReq.getIsDefault() != null && imgReq.getIsDefault());
        image.setDisplayOrder(imgReq.getDisplayOrder() != null ? imgReq.getDisplayOrder() : order);

        // Nếu blobPath trong request khác với blobPath hiện tại của ảnh -> ảnh đã được thay thế
        if (StringUtils.hasText(imgReq.getBlobPath()) && !imgReq.getBlobPath().equals(oldBlobPathThisImage)) {
          if (StringUtils.hasText(oldBlobPathThisImage)) {
            blobPathsToDeleteAfterCommit.add(oldBlobPathThisImage); // Đánh dấu blob cũ để xóa
          }
          image.setBlobPath(imgReq.getBlobPath()); // Cập nhật blob mới
        }
        requestImageIds.add(imgReq.getId()); // Đánh dấu ảnh này được giữ lại/cập nhật
      } else {
        // Ảnh mới
        image = new ProductImage();
        image.setProduct(product);
        if (!StringUtils.hasText(imgReq.getBlobPath())) {
          log.warn("New product image request is missing blobPath. Skipping image: {}", imgReq);
          continue; // Bỏ qua ảnh này nếu không có blobPath
        }
        image.setBlobPath(imgReq.getBlobPath());
        image.setDisplayOrder(order);
        image.setDefault(imgReq.getIsDefault() != null && imgReq.getIsDefault());
      }
      order++;
      if (image.isDefault()) {
        if (hasDefault) image.setDefault(false); // Chỉ cho phép 1 default
        else hasDefault = true;
      }

      updatedImages.add(image);
    }
    // Đảm bảo có ảnh default nếu list không rỗng và chưa có default nào được set từ request
    if (!updatedImages.isEmpty() && !hasDefaultInRequest) {
      updatedImages.stream()
              .min(Comparator.comparingInt(ProductImage::getDisplayOrder).thenComparing(ProductImage::getId)) // Thêm thenComparing để ổn định nếu displayOrder bằng nhau
              .ifPresent(img -> img.setDefault(true));
    }

    // Xác định các ảnh cũ cần xóa khỏi DB và storage
    List<ProductImage> imagesToRemoveFromDb = new ArrayList<>();
    for (Map.Entry<Long, ProductImage> entry : existingImagesMap.entrySet()) {
      if (!requestImageIds.contains(entry.getKey())) { // Nếu ảnh cũ không có trong request mới
        imagesToRemoveFromDb.add(entry.getValue());
        if (StringUtils.hasText(entry.getValue().getBlobPath())) {
          blobPathsToDeleteAfterCommit.add(entry.getValue().getBlobPath());
        }
      }
    }

    // Xóa các entity ProductImage khỏi DB (orphanRemoval=true sẽ xử lý nếu ProductImage là owned-side)
    // Hoặc xóa trực tiếp nếu cần
    if (!imagesToRemoveFromDb.isEmpty()) {
      productImageRepository.deleteAll(imagesToRemoveFromDb);
      log.debug("Marked {} old image entities for deletion from DB for product {}", imagesToRemoveFromDb.size(), product.getId());
    }

    // Cập nhật collection trong product entity (quan trọng cho orphanRemoval và để Hibernate quản lý)
    product.getImages().clear();
    product.getImages().addAll(updatedImages);
    // Việc lưu các ProductImage mới hoặc cập nhật sẽ được thực hiện khi product được lưu (do CascadeType.ALL)

    // Đăng ký việc xóa các blob cũ sau khi transaction commit thành công
    if (!blobPathsToDeleteAfterCommit.isEmpty()) {
      registerBlobsForDeletionAfterCommit(blobPathsToDeleteAfterCommit);
    }
  }


  /**
   * Đăng ký một danh sách các blobPath để xóa khỏi storage sau khi transaction hiện tại commit thành công.
   * Nếu không có transaction nào đang active, việc xóa sẽ được thực hiện ngay lập tức (cẩn thận).
   *
   * @param blobPaths Danh sách các blobPath cần xóa.
   */
  private void registerBlobsForDeletionAfterCommit(List<String> blobPaths) {
    if (blobPaths == null || blobPaths.isEmpty()) {
      return;
    }

    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          log.info("Transaction committed. Proceeding to delete {} blob(s) from storage.", blobPaths.size());
          deleteBlobsFromStorage(blobPaths);
        }
        // Bạn cũng có thể implement afterCompletion để xử lý trường hợp rollback nếu cần
        // @Override
        // public void afterCompletion(int status) {
        //     if (status == STATUS_ROLLED_BACK) {
        //         log.warn("Transaction rolled back. Blobs scheduled for deletion will not be deleted: {}", blobPaths);
        //     }
        // }
      });
    } else {
      // Không có transaction active, có thể là môi trường test hoặc một luồng không được quản lý transaction.
      // Trong trường hợp này, xóa ngay lập tức có thể rủi ro nếu có lỗi sau đó.
      // Hoặc bạn có thể quyết định không xóa và log lại.
      log.warn("No active transaction. Deleting {} blob(s) immediately. This might be risky if subsequent operations fail. Blobs: {}", blobPaths.size(), blobPaths);
      deleteBlobsFromStorage(blobPaths);
    }
  }


  /**
   * Thực hiện xóa các blob từ storage.
   * @param blobPaths Danh sách blobPath cần xóa.
   */
  private void deleteBlobsFromStorage(List<String> blobPaths) {
    for (String blobPath : blobPaths) {
      try {
        fileStorageService.delete(blobPath);
        log.info("Successfully deleted blob from storage: {}", blobPath);
      } catch (Exception e) {
        // Quan trọng: Log lỗi nhưng KHÔNG NÉM LẠI EXCEPTION ở đây
        // để không làm ảnh hưởng đến flow chính nếu việc xóa file thất bại.
        // Việc xóa file thất bại có thể được xử lý bằng một cơ chế dọn dẹp định kỳ khác.
        log.error("Failed to delete blob from storage after commit: {}. Error: {}", blobPath, e.getMessage(), e);
        // Có thể gửi thông báo cho admin hoặc ghi vào một hàng đợi lỗi để xử lý sau.
      }
    }
  }

  // ****** HÀM HELPER TRÍCH XUẤT SUBFOLDER TỪ BLOBPATH ******
  // Giả định blobPath có dạng "subFolder/filename.ext"
  private String extractSubFolderFromBlobPath(String blobPath) {
    if (blobPath == null) return null;
    int lastSlash = blobPath.lastIndexOf('/');
    if (lastSlash > 0) { // Nếu có dấu / và không phải ở đầu
      return blobPath.substring(0, lastSlash);
    } else if (lastSlash == -1 && StringUtils.hasText(StringUtils.getFilename(blobPath))) {
      // Nếu không có dấu / nhưng có tên file -> nằm ở thư mục gốc (subFolder là "")
      return "";
    }
    // Trường hợp khác (vd: chỉ có tên thư mục, hoặc path không hợp lệ)
    log.warn("Could not determine subfolder from blobPath: {}", blobPath);
    return null; // Hoặc trả về thư mục mặc định nếu muốn
  }

  // ******************************************************
  // Hàm helper đảm bảo có default image
  private void checkAndSetDefaultImage(Set<ProductImage> images) {
    if (!images.isEmpty() && images.stream().noneMatch(ProductImage::isDefault)) {
      images.stream()
          .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
          .ifPresent(img -> img.setDefault(true));
    }
  }

  private String extractBlobPathFromUrl(String imageUrl) {
    if (publicBaseUrl != null && imageUrl != null && imageUrl.startsWith(publicBaseUrl)) {
      try {
        String pathPart = imageUrl.substring(publicBaseUrl.length());
        if (pathPart.startsWith("/")) {
          pathPart = pathPart.substring(1);
        }
        // Decode URL encoding
        return java.net.URLDecoder.decode(pathPart, StandardCharsets.UTF_8.toString());
      } catch (Exception e) {
        log.error("Could not decode blob path from public URL: {}", imageUrl, e);
      }
    }
    // Nếu không phải public URL hoặc có lỗi, trả về null
    log.warn("Could not extract blob path from URL (might be signed URL or invalid): {}", imageUrl);
    return null;
  }

  // ****** HÀM HELPER TRÍCH XUẤT SUBFOLDER (VÍ DỤ) ******
  private String extractSubFolderFromUrl(String imageUrl) {
    // Logic này cần dựa trên cấu trúc URL của bạn
    // Ví dụ: Nếu URL là /api/files/download/product_images/abc.jpg
    String downloadPrefix = "/api/files/download/";
    if (imageUrl != null && imageUrl.contains(downloadPrefix)) {
      String pathPart =
          imageUrl.substring(imageUrl.indexOf(downloadPrefix) + downloadPrefix.length());
      int lastSlash = pathPart.lastIndexOf('/');
      if (lastSlash > 0) { // Đảm bảo có thư mục con
        return pathPart.substring(0, lastSlash);
      }
    }
    return null; // Hoặc trả về thư mục mặc định nếu không trích xuất được
  }

  //    // Helper xử lý cập nhật bậc giá B2B
  //    private void updatePricingTiers(Product product, Boolean isB2bAvailable,
  // List<ProductPricingTierRequest> tierRequests) {
  //        if (tierRequests != null) { // Chỉ xử lý nếu có gửi list (kể cả rỗng)
  //            pricingTierRepository.deleteByProductId(product.getId()); // Xóa bậc giá cũ
  //            product.getPricingTiers().clear(); // Xóa khỏi collection
  //
  //            if (Boolean.TRUE.equals(isB2bAvailable) && !CollectionUtils.isEmpty(tierRequests)) {
  //                Set<ProductPricingTier> tiers = tierRequests.stream()
  //                        .map(tierReq -> {
  //                            ProductPricingTier tier =
  // pricingTierMapper.requestToProductPricingTier(tierReq);
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
      List<Product> sameCategoryProducts =
          productRepository.findTopNByCategoryIdAndIdNotAndStatus(
              currentProduct.getCategory().getId(),
              currentProduct.getId(),
              ProductStatus.PUBLISHED,
              limit);
      related.addAll(sameCategoryProducts);
      // Thêm ID của các sản phẩm này vào danh sách loại trừ cho bước sau
      related.forEach(p -> excludedIds.add(p.getId()));
    }

    // 2. Nếu chưa đủ, tìm thêm sản phẩm cùng nông dân
    int remainingLimit = RELATED_PRODUCTS_LIMIT - related.size();
    if (remainingLimit > 0 && currentProduct.getFarmer() != null) {
      Pageable limit = PageRequest.of(0, remainingLimit);
      List<Product> sameFarmerProducts =
          productRepository.findTopNByFarmerIdAndIdNotInAndStatus(
              currentProduct.getFarmer().getId(),
              excludedIds, // Loại trừ sản phẩm hiện tại và sp cùng category đã lấy
              ProductStatus.PUBLISHED,
              limit);
      related.addAll(sameFarmerProducts);
    }

    // Map kết quả sang DTO Summary
    if (related.isEmpty()) {
      return Collections.emptyList();
    }
    return productMapper.toProductSummaryResponseList(related);
  }
}
