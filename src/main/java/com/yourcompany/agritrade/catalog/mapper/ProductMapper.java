package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    uses = {
      CategoryMapper.class,
      ProductImageMapper.class,
      FarmerInfoMapper.class // Dùng mapper riêng cho FarmerInfo
    })
public abstract class ProductMapper {

  @Autowired protected FarmerInfoMapper farmerInfoMapper;

  @Autowired protected FileStorageService fileStorageService;

  // --- Response Mappers ---

  @Mapping(target = "thumbnailUrl", source = "images", qualifiedByName = "getDefaultImageUrl")
  @Mapping(
      target = "farmerInfo",
      source = "product",
      qualifiedByName = "mapProductToFarmerInfo") // Dùng method tùy chỉnh
  @Mapping(target = "category", source = "category")
  @Mapping(target = "b2bEnabled", source = "b2bEnabled")
  public abstract ProductSummaryResponse toProductSummaryResponse(Product product);

  @AfterMapping
  protected void setIsNewFlag(@MappingTarget ProductSummaryResponse dto, Product product) {
    if (product.getCreatedAt() != null) {
      //  Sản phẩm tạo trong vòng 7 ngày gần nhất được coi là mới
      long daysSinceCreation = ChronoUnit.DAYS.between(product.getCreatedAt(), LocalDateTime.now());
      dto.setNew(daysSinceCreation <= 7);
    } else {
      dto.setNew(false); // Hoặc true nếu muốn coi sp không có ngày tạo là mới?
    }
  }

  public abstract List<ProductSummaryResponse> toProductSummaryResponseList(List<Product> products);

  @Mapping(target = "category", source = "category") // MapStruct dùng CategoryMapper
  @Mapping(
      target = "farmer",
      source = "product",
      qualifiedByName = "mapProductToFarmerInfo") // Dùng method tùy chỉnh
  @Mapping(target = "images", source = "images") // MapStruct dùng ProductImageMapper (cho List)
  // relatedProducts cần xử lý riêng trong service, không map ở đây
  @Mapping(target = "relatedProducts", ignore = true)
  @Mapping(target = "b2bEnabled", source = "b2bEnabled")
  public abstract ProductDetailResponse toProductDetailResponse(Product product);

  // --- Request Mapper ---
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "slug", ignore = true) // Slug sẽ được tạo trong service
  @Mapping(target = "farmer", ignore = true) // Farmer sẽ được set trong service
  @Mapping(target = "category", ignore = true) // Category sẽ được set trong service
  @Mapping(target = "provinceCode", ignore = true) // Lấy từ farmer profile
  @Mapping(target = "averageRating", ignore = true)
  @Mapping(target = "ratingCount", ignore = true)
  @Mapping(target = "favoriteCount", ignore = true)
  @Mapping(target = "images", ignore = true) // Sẽ xử lý riêng trong service
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true) // Bỏ qua version khi tạo mới
  @Mapping(target = "rejectReason", ignore = true) // Bỏ qua rejectReason khi tạo mới
  @Mapping(target = "b2bEnabled", source = "b2bEnabled")
  public abstract Product requestToProduct(ProductRequest request);

  // --- Update Mapper ---
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "farmer", ignore = true)
  @Mapping(target = "category", ignore = true) // Category xử lý riêng
  @Mapping(target = "slug", ignore = true) // Slug xử lý riêng
  @Mapping(target = "status", ignore = true) // Status xử lý riêng
  @Mapping(target = "averageRating", ignore = true)
  @Mapping(target = "ratingCount", ignore = true)
  @Mapping(target = "favoriteCount", ignore = true)
  @Mapping(target = "images", ignore = true) // <-- Bỏ qua collection
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "b2bEnabled", source = "b2bEnabled")
  @Mapping(target = "version", ignore = true) // Không map version
  public abstract void updateProductFromRequest(
      ProductRequest request, @MappingTarget Product product);

  @Mapping(
      target = "thumbnailUrl",
      source = "images",
      qualifiedByName = "getDefaultImageUrl") // Lấy ảnh thumbnail
  public abstract ProductInfoResponse toProductInfoResponse(Product product);

  // --- Helper Methods ---
  @Named("getDefaultImageUrl")
  String getDefaultImageUrl(Set<ProductImage> images) {
    if (images == null || images.isEmpty()) {

      return "assets/images/placeholder-image.png"; // Trả về placeholder nếu không có ảnh
    }
    Optional<ProductImage> imageOpt =
        images.stream()
            .filter(ProductImage::isDefault)
            .findFirst()
            .or(() -> images.stream().min(Comparator.comparingInt(ProductImage::getDisplayOrder)));

    if (imageOpt.isPresent() && imageOpt.get().getBlobPath() != null) {
      try {
        // Gọi service để lấy URL mới nhất (Signed URL)
        return fileStorageService.getFileUrl(imageOpt.get().getBlobPath());
      } catch (Exception e) {

        return "assets/images/placeholder-image.png";
      }
    }
    return "assets/images/placeholder-image.png";
  }

  @Named("mapProductToFarmerInfo")
  protected FarmerInfoResponse mapProductToFarmerInfo(Product product) {
    if (product == null || product.getFarmer() == null) {
      return null;
    }
    User farmer = product.getFarmer();

    FarmerProfile profile = farmer.getFarmerProfile();

    FarmerInfoResponse info = farmerInfoMapper.toFarmerInfoResponse(farmer, profile);

    // Fallback logic
    if (info != null
        && (info.getFarmName() == null || info.getFarmName().isEmpty())
        && farmer.getFullName() != null) {
      info.setFarmName(farmer.getFullName()); // Dùng fullName của User làm dự phòng
    }

    return info;
  }
}
