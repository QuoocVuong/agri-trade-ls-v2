package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.dto.response.ProductInfoResponse;
import com.yourcompany.agritrade.ordering.domain.OrderItem;
import com.yourcompany.agritrade.ordering.dto.response.OrderItemResponse;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring") // Không cần uses nếu ProductInfoResponse đơn giản
public interface OrderItemMapper {

  // Map Product sang ProductInfoResponse (nếu cần)
  // MapStruct sẽ tự tìm các trường id, name, slug
  @Mapping(
      target = "thumbnailUrl",
      source = "product",
      qualifiedByName = "mapProductToThumbnailUrl") // Lấy ảnh từ ProductMapper nếu cần
  ProductInfoResponse productToProductInfoResponse(Product product);

  // Lưu ý: Cần có phương thức getDefaultImageUrl trong ProductMapper hoặc ở đây

  // Map OrderItem sang OrderItemResponse
  @Mapping(target = "product", source = "product") // Map Product entity sang ProductInfoResponse
  OrderItemResponse toOrderItemResponse(OrderItem orderItem);

  List<OrderItemResponse> toOrderItemResponseList(List<OrderItem> orderItems);

  //    // Helper method để lấy ảnh default (có thể copy từ ProductMapper hoặc gọi ProductMapper)
  //    @Named("getDefaultImageUrl")
  //    default String getDefaultImageUrlFromOrderItem(Product product) {
  //        if (product == null || product.getImages() == null || product.getImages().isEmpty()) {
  //            return null;
  //        }
  //        return product.getImages().stream()
  //                .filter(img -> img.isDefault())
  //                .findFirst()
  //                .or(() -> product.getImages().stream().findFirst()) // Lấy cái đầu tiên nếu
  // không có default
  //                .map(img -> img.getImageUrl())
  //                .orElse(null);
  //    }
  // Helper method để lấy ảnh thumbnail từ Product
  @Named("mapProductToThumbnailUrl") // Đặt tên cho @Named
  default String mapProductToThumbnailUrl(Product product) {
    if (product == null || product.getImages() == null || product.getImages().isEmpty()) {
      return null;
    }
    // Ưu tiên ảnh default
    String defaultImageUrl =
        product.getImages().stream()
            .filter(ProductImage::isDefault) // Sử dụng method reference
            .findFirst()
            .map(ProductImage::getImageUrl) // Sử dụng method reference
            .orElse(null);

    if (defaultImageUrl != null) {
      return defaultImageUrl;
    }

    // Nếu không có ảnh default, lấy ảnh đầu tiên theo displayOrder
    // Hoặc nếu không có displayOrder, lấy ảnh đầu tiên trong danh sách
    return product.getImages().stream()
        .min(Comparator.comparingInt(ProductImage::getDisplayOrder)) // Sắp xếp theo displayOrder
        .map(ProductImage::getImageUrl)
        .orElse(
            // Fallback: nếu không có displayOrder hoặc muốn lấy cái đầu tiên bất kể thứ tự
            product.getImages().stream().findFirst().map(ProductImage::getImageUrl).orElse(null));
  }
}
