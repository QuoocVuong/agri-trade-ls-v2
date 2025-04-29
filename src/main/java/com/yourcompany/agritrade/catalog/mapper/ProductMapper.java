package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage; // Import
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.usermanagement.domain.User; // Import User
import org.mapstruct.*;

import java.util.Comparator; // Import Comparator
import java.util.List; // Import List
import java.util.Optional; // Import Optional
import java.util.Set;

// Khai báo uses các mapper khác
@Mapper(componentModel = "spring", uses = {
        CategoryMapper.class,
        ProductImageMapper.class,
        ProductPricingTierMapper.class, // Thêm nếu dùng
        FarmerInfoMapper.class // Dùng mapper riêng cho FarmerInfo
})
public interface ProductMapper {

    // --- Response Mappers ---

    @Mapping(target = "thumbnailUrl", source = "images", qualifiedByName = "getDefaultImageUrl")
    @Mapping(target = "farmerInfo", source = "farmer", qualifiedByName = "mapFarmerToInfo") // Dùng method tùy chỉnh
    ProductSummaryResponse toProductSummaryResponse(Product product);

    List<ProductSummaryResponse> toProductSummaryResponseList(List<Product> products);

    @Mapping(target = "category", source = "category") // MapStruct dùng CategoryMapper
    @Mapping(target = "farmer", source = "farmer", qualifiedByName = "mapFarmerToInfo") // Dùng method tùy chỉnh
    @Mapping(target = "images", source = "images") // MapStruct dùng ProductImageMapper (cho List)
    @Mapping(target = "pricingTiers", source = "pricingTiers") // MapStruct dùng ProductPricingTierMapper (cho List)





    ProductDetailResponse toProductDetailResponse(Product product);

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
    @Mapping(target = "pricingTiers", ignore = true) // Sẽ xử lý riêng trong service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    //@Mapping(target = "isDeleted", ignore = true)
    Product requestToProduct(ProductRequest request);

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
    @Mapping(target = "pricingTiers", ignore = true) // <-- Bỏ qua collection
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
   // @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "version", ignore = true) // Không map version
    //@Mapping(target = "isDeleted", ignore = true)
    void updateProductFromRequest(ProductRequest request, @MappingTarget Product product);

    // --- Helper Methods ---
    @Named("getDefaultImageUrl")
    default String getDefaultImageUrl(Set<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null; // Hoặc trả về URL ảnh placeholder
        }
        // Ưu tiên ảnh có isDefault = true
        Optional<ProductImage> defaultImage = images.stream().filter(ProductImage::isDefault).findFirst();
        if (defaultImage.isPresent()) {
            return defaultImage.get().getImageUrl();
        }
        // Nếu không có ảnh default, lấy ảnh đầu tiên (theo ID hoặc createdAt)
        return images.stream()
                .min(Comparator.comparing(ProductImage::getId)) // Ví dụ lấy theo ID nhỏ nhất
                .map(ProductImage::getImageUrl)
                .orElse(null); // Hoặc placeholder
    }

    // Sử dụng FarmerInfoMapper đã inject (cần đổi ProductMapper thành abstract class)
     /*
     @Autowired
     protected FarmerInfoMapper farmerInfoMapper;

     @Named("mapFarmerToInfo")
     default FarmerInfoResponse mapFarmerToInfo(User farmer) {
         if (farmer == null) return null;
         // Cần lấy FarmerProfile tương ứng nếu FarmerInfoMapper yêu cầu
         // FarmerProfile profile = farmerProfileRepository.findById(farmer.getId()).orElse(null);
         // return farmerInfoMapper.toFarmerInfoResponse(farmer, profile);
         // Hoặc dùng mapper đơn giản hơn nếu chỉ cần thông tin từ User
         return farmerInfoMapper.userToFarmerInfoResponse(farmer);
     }
     */
    // Hoặc map thủ công nếu không muốn inject repo vào mapper
    @Named("mapFarmerToInfo")
    default FarmerInfoResponse mapFarmerToInfoManual(User farmer) {
        if (farmer == null) return null;
        FarmerInfoResponse info = new FarmerInfoResponse();
        info.setFarmerId(farmer.getId());
        info.setFarmerAvatarUrl(farmer.getAvatarUrl());
        // Cần truy vấn hoặc có sẵn FarmerProfile để lấy farmName và provinceCode
        // info.setFarmName(farmer.getFarmerProfile().getFarmName()); // Ví dụ nếu có quan hệ trực tiếp
        // info.setProvinceCode(farmer.getFarmerProfile().getProvinceCode());
        return info;
    }

}
// Lưu ý: Để dùng @Autowired trong Mapper, cần đổi thành abstract class thay vì interface.
// Tuy nhiên, việc inject Repository vào Mapper không phải là best practice.
// Cách tốt hơn là Service sẽ lấy User và FarmerProfile, sau đó gọi một phương thức map nhận cả 2 đối tượng.
// Hoặc tạo một DTO trung gian chỉ chứa thông tin cần thiết từ User và Profile để truyền vào mapper.
// Trong ví dụ này, tạm dùng mapFarmerToInfoManual và chấp nhận thiếu farmName/provinceCode hoặc sẽ xử lý ở Service.