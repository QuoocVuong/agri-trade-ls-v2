package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequest;
import com.yourcompany.agritrade.ordering.dto.response.SupplyOrderRequestResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, ProductMapper.class})
public interface SupplyOrderRequestMapper {

  @Mapping(target = "buyer", source = "buyer")
  @Mapping(target = "farmer", source = "farmer")
  @Mapping(
      target = "product",
      source = "product") // MapStruct sẽ dùng toProductInfoResponse từ ProductMapper
  SupplyOrderRequestResponse toSupplyOrderRequestResponse(SupplyOrderRequest supplyOrderRequest);

  // Không cần mapper từ Request DTO sang Entity ở đây vì Service sẽ làm thủ công
}
