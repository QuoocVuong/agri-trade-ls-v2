package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.dto.response.ProductInfoResponse; // Import ProductInfoResponse
import com.yourcompany.agritrade.ordering.domain.OrderItem;
import com.yourcompany.agritrade.ordering.dto.response.OrderItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring") // Không cần uses nếu ProductInfoResponse đơn giản
public interface OrderItemMapper {

    // Map Product sang ProductInfoResponse (nếu cần)
    // MapStruct sẽ tự tìm các trường id, name, slug
    //@Mapping(target = "thumbnailUrl", source = "images", qualifiedByName = "getDefaultImageUrl") // Lấy ảnh từ ProductMapper nếu cần
    ProductInfoResponse productToProductInfoResponse(Product product);
    // Lưu ý: Cần có phương thức getDefaultImageUrl trong ProductMapper hoặc ở đây

    // Map OrderItem sang OrderItemResponse
    @Mapping(target = "product", source = "product") // Map Product entity sang ProductInfoResponse
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    List<OrderItemResponse> toOrderItemResponseList(List<OrderItem> orderItems);

    // Helper method để lấy ảnh default (có thể copy từ ProductMapper hoặc gọi ProductMapper)
    @Named("getDefaultImageUrl")
    default String getDefaultImageUrlFromOrderItem(Product product) {
        if (product == null || product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().stream()
                .filter(img -> img.isDefault())
                .findFirst()
                .or(() -> product.getImages().stream().findFirst()) // Lấy cái đầu tiên nếu không có default
                .map(img -> img.getImageUrl())
                .orElse(null);
    }
}