package com.yourcompany.agritrade.usermanagement.mapper;

import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FarmerSummaryMapper {

    // Phương thức chính để map từ User và FarmerProfile (nếu có)
    // Cần được gọi từ Service sau khi đã lấy cả User và FarmerProfile
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "farmName", source = "profile.farmName") // Lấy từ profile
    @Mapping(target = "fullName", source = "user.fullName")   // Lấy từ user
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")  // Lấy từ user
    @Mapping(target = "provinceCode", source = "profile.provinceCode") // Lấy từ profile
    @Mapping(target = "followerCount", source = "user.followerCount") // Lấy từ user
    FarmerSummaryResponse toFarmerSummaryResponse(User user, FarmerProfile profile);

    // Phương thức phụ (fallback) nếu không tìm thấy FarmerProfile
    // Hoặc bạn có thể xử lý null trong service thay vì tạo hàm riêng
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "farmName", ignore = true) // Không có profile nên bỏ qua
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "provinceCode", ignore = true) // Không có profile nên bỏ qua
    @Mapping(target = "followerCount", source = "followerCount")
    FarmerSummaryResponse userToFarmerSummaryResponse(User user);

    // Không cần map List ở đây vì Service sẽ xử lý từng cặp User/Profile
}