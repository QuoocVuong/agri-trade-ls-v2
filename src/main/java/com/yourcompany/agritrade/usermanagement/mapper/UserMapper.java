package com.yourcompany.agritrade.usermanagement.mapper;

import com.yourcompany.agritrade.interaction.websocket.WebSocketEventListener;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    uses = {FarmerProfileMapper.class, BusinessProfileMapper.class}) // Khai báo uses
public abstract class UserMapper { // Đổi thành abstract class để inject mapper khác

  @Autowired // Inject các mapper phụ thuộc
  protected FarmerProfileMapper farmerProfileMapper;
  @Autowired protected BusinessProfileMapper businessProfileMapper;

  // ****** INJECT WebSocketEventListener ******
  @Autowired protected WebSocketEventListener presenceService;

  // ***************************************

  // Chỉ map các trường cơ bản từ User sang UserResponse
  @Mapping(source = "roles", target = "roles", qualifiedByName = "rolesToRoleNames")

  @Named("toUserResponse") // *** Đặt tên cho phương thức nà
  public abstract UserResponse toUserResponse(User user);

  // Mapping cho UserProfileResponse (kết hợp)
  @Mapping(source = "roles", target = "roles", qualifiedByName = "rolesToRoleNames")
  // Map farmerProfile và businessProfile nếu tồn tại (cần có getter trong User entity hoặc lấy từ
  // repo)
  // Giả sử bạn đã thêm getter hoặc sẽ lấy riêng trong service
  //    @Mapping(source = "farmerProfile", target = "farmerProfile") // MapStruct sẽ dùng
  // FarmerProfileMapper
  //    @Mapping(source = "businessProfile", target = "businessProfile") // MapStruct sẽ dùng
  // BusinessProfileMapper
  // MapStruct sẽ tự động map các trường giống tên từ User sang các trường kế thừa trong
  // UserProfileResponse
  // Các trường farmerProfile và businessProfile sẽ là null sau khi map này, và được gán giá trị
  // trong Service
  public abstract UserProfileResponse toUserProfileResponse(User user);

  // ****** THÊM PHƯƠNG THỨC MAP SANG UserInfoSimpleResponse ******
  @Mapping(target = "id", source = "id")
  @Mapping(target = "fullName", source = "fullName")
  @Mapping(target = "avatarUrl", source = "avatarUrl")
  // Thêm mapping cho isOnline, gọi hàm từ presenceService
  @Mapping(target = "online", expression = "java(presenceService.isUserOnline(user.getId()))")
  public abstract UserInfoSimpleResponse toUserInfoSimpleResponse(User user);

  // ***********************************************************

  // ****** THÊM PHƯƠNG THỨC MAP LIST SANG UserInfoSimpleResponse ******
  public abstract List<UserInfoSimpleResponse> toUserInfoSimpleResponseList(List<User> users);

  // ****************************************************************

  @Named("rolesToRoleNames")
  Set<String> rolesToRoleNames(Set<Role> roles) { // Bỏ default khi là abstract class
    if (roles == null) {
      return null;
    }
    return roles.stream().map(role -> role.getName().name()).collect(Collectors.toSet());
  }
}
