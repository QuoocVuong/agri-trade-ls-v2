package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.domain.ProductPricingTier;
import com.yourcompany.agritrade.catalog.dto.request.ProductPricingTierRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductPricingTierResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductPricingTierMapper {
  ProductPricingTierResponse toProductPricingTierResponse(ProductPricingTier tier);

  List<ProductPricingTierResponse> toProductPricingTierResponseList(List<ProductPricingTier> tiers);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "product", ignore = true)
  ProductPricingTier requestToProductPricingTier(ProductPricingTierRequest request);
}
