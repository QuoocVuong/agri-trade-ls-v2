package com.yourcompany.agritradels.interaction.mapper;

import com.yourcompany.agritradels.interaction.domain.ChatMessage;
import com.yourcompany.agritradels.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritradels.usermanagement.mapper.UserMapper; // Import UserMapper để lấy UserInfoSimpleResponse
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired

import java.util.List;

// uses UserMapper để map thông tin sender và recipient
@Mapper(componentModel = "spring", uses = {UserMapper.class})
public abstract class ChatMessageMapper { // Đổi thành abstract class

    @Autowired // Inject UserMapper
    protected UserMapper userMapper;

    @Mapping(target = "roomId", source = "room.id")
    // MapStruct sẽ tự động tìm phương thức phù hợp trong UserMapper để map User sang UserInfoSimpleResponse
    // nếu bạn đã định nghĩa phương thức đó trong UserMapper.
    // Nếu chưa có, bạn cần tạo phương thức ví dụ: UserInfoSimpleResponse userToUserInfoSimpleResponse(User user) trong UserMapper
    @Mapping(target = "sender", source = "sender") // Giả sử UserMapper có hàm map sang UserInfoSimpleResponse
    @Mapping(target = "recipient", source = "recipient") // Giả sử UserMapper có hàm map sang UserInfoSimpleResponse
    public abstract ChatMessageResponse toChatMessageResponse(ChatMessage chatMessage);

    public abstract List<ChatMessageResponse> toChatMessageResponseList(List<ChatMessage> messages);

    // Nếu UserMapper chưa có hàm map sang UserInfoSimpleResponse, bạn có thể định nghĩa ở đây
    // Hoặc tốt hơn là định nghĩa trong UserMapper và gọi nó ở đây nếu cần thiết (thông qua @Named)
    /*
    @Mapping(target="...", source="...") // Map các trường cần thiết
    abstract UserInfoSimpleResponse userToUserInfoSimple(User user);
    */
}