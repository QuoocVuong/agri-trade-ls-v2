package com.yourcompany.agritrade.usermanagement.mapper;

import com.yourcompany.agritrade.usermanagement.domain.BusinessProfile;
import com.yourcompany.agritrade.usermanagement.dto.request.BusinessProfileRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.BusinessProfileResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface BusinessProfileMapper {

    @Mapping(target = "userId", source = "user.id")
        // MapStruct tự động map các trường cùng tên
    BusinessProfileResponse toBusinessProfileResponse(BusinessProfile businessProfile);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BusinessProfile requestToBusinessProfile(BusinessProfileRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateBusinessProfileFromRequest(BusinessProfileRequest request, @MappingTarget BusinessProfile businessProfile);
}