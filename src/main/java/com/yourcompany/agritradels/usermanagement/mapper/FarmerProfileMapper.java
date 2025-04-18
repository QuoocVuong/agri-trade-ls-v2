package com.yourcompany.agritradels.usermanagement.mapper;

import com.yourcompany.agritradels.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritradels.usermanagement.domain.User;
import com.yourcompany.agritradels.usermanagement.dto.request.FarmerProfileRequest;
import com.yourcompany.agritradels.usermanagement.dto.response.FarmerProfileResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface FarmerProfileMapper {

    @Mapping(target = "userId", source = "user.id") // Lấy userId từ User entity lồng nhau
    @Mapping(target = "verifiedByAdminName", source = "verifiedBy", qualifiedByName = "adminToName") // Map admin duyệt
    FarmerProfileResponse toFarmerProfileResponse(FarmerProfile farmerProfile);

    // Map từ Request DTO sang Entity, bỏ qua các trường không có trong request
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "verificationStatus", ignore = true)
    @Mapping(target = "verifiedAt", ignore = true)
    @Mapping(target = "verifiedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    FarmerProfile requestToFarmerProfile(FarmerProfileRequest request);

    // Cập nhật entity từ request DTO, bỏ qua các trường null trong request
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "verificationStatus", ignore = true)
    @Mapping(target = "verifiedAt", ignore = true)
    @Mapping(target = "verifiedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFarmerProfileFromRequest(FarmerProfileRequest request, @MappingTarget FarmerProfile farmerProfile);

    // Helper method để lấy tên admin
    @Named("adminToName")
    default String adminToName(User admin) {
        return (admin != null) ? admin.getFullName() : null;
    }
}