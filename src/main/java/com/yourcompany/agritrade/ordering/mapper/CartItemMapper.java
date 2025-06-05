package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.ordering.domain.CartItem;
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

// uses ProductMapper để map thông tin sản phẩm
@Mapper(
    componentModel = "spring",
    uses = {ProductMapper.class})
public abstract class CartItemMapper {

  @Autowired // Inject ProductMapper để lấy thông tin tóm tắt
  protected ProductMapper productMapper;

  // Map từ CartItem sang CartItemResponse
  // product sẽ được map tự động nếu tên giống, nhưng chúng ta cần ProductSummaryResponse
  @Mapping(target = "product", ignore = true) // Bỏ qua map tự động product
  @Mapping(target = "itemTotal", ignore = true) // itemTotal sẽ tính sau
  public abstract CartItemResponse toCartItemResponse(CartItem cartItem);

  public abstract List<CartItemResponse> toCartItemResponseList(List<CartItem> cartItems);

  // Sử dụng @AfterMapping để lấy ProductSummaryResponse và tính itemTotal
  @AfterMapping
  protected void afterMappingToCartItemResponse(
      CartItem cartItem, @MappingTarget CartItemResponse response) {
    if (cartItem != null && cartItem.getProduct() != null) {
      // Map product sang summary response
      response.setProduct(productMapper.toProductSummaryResponse(cartItem.getProduct()));
      // Tính itemTotal
      if (response.getProduct() != null
          && response.getProduct().getPrice() != null
          && cartItem.getQuantity() > 0) {
        response.setItemTotal(
            response.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
      } else {
        response.setItemTotal(BigDecimal.ZERO);
      }
    }
  }
}
