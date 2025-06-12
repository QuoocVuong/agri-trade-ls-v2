package com.yourcompany.agritrade.interaction.mapper;

import com.yourcompany.agritrade.interaction.domain.ChatMessage;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class})
public interface ChatMessageMapper {

  @Mapping(target = "roomId", source = "room.id")
  // MapStruct sẽ dùng UserMapper trong 'uses' để gọi hàm map User -> UserInfoSimpleResponse
  @Mapping(target = "sender", source = "sender")
  @Mapping(target = "recipient", source = "recipient")
  ChatMessageResponse toChatMessageResponse(
      ChatMessage chatMessage); // Đổi về non-abstract nếu là interface

  List<ChatMessageResponse> toChatMessageResponseList(
      List<ChatMessage> messages); // Đổi về non-abstract
}
