package com.yourcompany.agritradels.catalog.mapper;

import com.yourcompany.agritradels.catalog.domain.ProductPricingTier;
import com.yourcompany.agritradels.catalog.dto.request.ProductPricingTierRequest;
import com.yourcompany.agritradels.catalog.dto.response.ProductPricingTierResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductPricingTierMapper {
    ProductPricingTierResponse toProductPricingTierResponse(ProductPricingTier tier);
    List<ProductPricingTierResponse> toProductPricingTierResponseList(List<ProductPricingTier> tiers);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ProductPricingTier requestToProductPricingTier(ProductPricingTierRequest request);
}