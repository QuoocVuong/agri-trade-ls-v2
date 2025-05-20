package com.yourcompany.agritrade.usermanagement.mapper;

import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AddressMapper {

  // Map từ Entity sang Response DTO
  @Mapping(source = "user.id", target = "userId") // Map user id
  AddressResponse toAddressResponse(Address address);

  List<AddressResponse> toAddressResponseList(List<Address> addresses);

  // Map từ Request DTO sang Entity (cho việc tạo mới)
  // Bỏ qua id, createdAt, updatedAt, user, isDeleted vì sẽ được set tự động hoặc trong service
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "user", ignore = true) // User sẽ được set trong service
  @Mapping(target = "deleted", ignore = true)
  Address requestToAddress(AddressRequest request);

  // Cập nhật Entity từ Request DTO (cho việc sửa)
  // Bỏ qua id, createdAt, user, isDeleted
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true) // MapStruct tự cập nhật nếu có @UpdateTimestamp
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  void updateAddressFromRequest(AddressRequest request, @MappingTarget Address address);
}
