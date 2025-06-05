package com.yourcompany.agritrade.catalog.mapper;

import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FarmerInfoMapper {

  // Map từ User và FarmerProfile  sang FarmerInfoResponse
  @Mapping(target = "farmerId", source = "user.id")
  @Mapping(target = "farmName", source = "profile.farmName") // Lấy từ profile
  @Mapping(target = "farmerAvatarUrl", source = "user.avatarUrl") // Lấy từ user
  @Mapping(target = "provinceCode", source = "profile.provinceCode") // Lấy từ profile
  FarmerInfoResponse toFarmerInfoResponse(User user, FarmerProfile profile);

  // Hoặc map chỉ từ User nếu không cần profile ngay lập tức
  @Mapping(target = "farmerId", source = "id")
  @Mapping(target = "farmerAvatarUrl", source = "avatarUrl")

  @Mapping(target = "farmName", ignore = true)
  @Mapping(target = "provinceCode", ignore = true)
  FarmerInfoResponse userToFarmerInfoResponse(User user);
}
