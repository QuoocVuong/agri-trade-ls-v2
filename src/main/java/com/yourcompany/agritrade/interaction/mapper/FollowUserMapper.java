package com.yourcompany.agritrade.interaction.mapper;

import com.yourcompany.agritrade.interaction.dto.response.FollowUserResponse;
import com.yourcompany.agritrade.usermanagement.domain.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FollowUserMapper {

  // Map từ User entity sang FollowUserResponse DTO
  @Mapping(target = "userId", source = "id") // Map id sang userId
  // MapStruct tự map fullName, avatarUrl
  // Cần thêm logic lấy farmName nếu cần
  FollowUserResponse toFollowUserResponse(User user);

  List<FollowUserResponse> toFollowUserResponseList(List<User> users);
}
