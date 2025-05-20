package com.yourcompany.agritrade.interaction.mapper;

import com.yourcompany.agritrade.interaction.domain.ChatMessage;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// uses UserMapper để map thông tin sender và recipient
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class})
public interface ChatMessageMapper {

  // @Autowired // Bỏ inject UserMapper nếu dùng uses
  // protected UserMapper userMapper;

  // Bỏ inject presenceService
  // @Autowired
  // protected WebSocketEventListener presenceService;

  @Mapping(target = "roomId", source = "room.id")
  // MapStruct sẽ dùng UserMapper trong 'uses' để gọi hàm map User -> UserInfoSimpleResponse
  @Mapping(target = "sender", source = "sender")
  @Mapping(target = "recipient", source = "recipient")
  ChatMessageResponse toChatMessageResponse(
      ChatMessage chatMessage); // Đổi về non-abstract nếu là interface

  List<ChatMessageResponse> toChatMessageResponseList(
      List<ChatMessage> messages); // Đổi về non-abstract

  // Bỏ hàm userToUserInfoSimple ở đây vì nó nên nằm trong UserMapper
  /*
  @Mapping(target = "id", source = "id")
  @Mapping(target = "fullName", source = "fullName")
  @Mapping(target = "avatarUrl", source = "avatarUrl")
  @Mapping(target = "isOnline", expression = "java(presenceService.isUserOnline(user.getId()))")
  abstract UserInfoSimpleResponse userToUserInfoSimple(User user);
  */
}
