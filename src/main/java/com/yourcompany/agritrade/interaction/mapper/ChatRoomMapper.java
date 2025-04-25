package com.yourcompany.agritrade.interaction.mapper;

import com.yourcompany.agritrade.interaction.domain.ChatRoom;
import com.yourcompany.agritrade.interaction.dto.response.ChatRoomResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper; // Import UserMapper
import org.mapstruct.AfterMapping; // Import AfterMapping
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget; // Import MappingTarget
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.security.core.Authentication; // Import Authentication
import org.springframework.security.core.context.SecurityContextHolder; // Import SecurityContextHolder

import java.util.List;

// uses UserMapper và ChatMessageMapper
@Mapper(componentModel = "spring", uses = {UserMapper.class, ChatMessageMapper.class})
public abstract class ChatRoomMapper {

    @Autowired
    protected UserMapper userMapper; // Để map user1, user2
    @Autowired
    protected ChatMessageMapper chatMessageMapper; // Để map lastMessage

    // Map các trường cơ bản
    @Mapping(target = "user1", source = "user1") // Dùng UserMapper -> UserInfoSimpleResponse
    @Mapping(target = "user2", source = "user2") // Dùng UserMapper -> UserInfoSimpleResponse
    @Mapping(target = "lastMessage", source = "lastMessage") // Dùng ChatMessageMapper
    @Mapping(target = "myUnreadCount", ignore = true) // Sẽ tính trong @AfterMapping
    @Mapping(target = "otherUser", ignore = true) // Sẽ tính trong @AfterMapping
    public abstract ChatRoomResponse toChatRoomResponse(ChatRoom chatRoom);

    public abstract List<ChatRoomResponse> toChatRoomResponseList(List<ChatRoom> chatRooms);

    // Tính toán các trường phụ sau khi map cơ bản xong
    @AfterMapping
    protected void afterMappingToChatRoomResponse(ChatRoom chatRoom, @MappingTarget ChatRoomResponse response) {
        if (chatRoom == null || response == null) {
            return;
        }

        // Lấy thông tin user đang đăng nhập để xác định "my" và "other"
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String currentUsername = authentication.getName(); // Lấy email/username

            // Xác định myUnreadCount và otherUser
            if (chatRoom.getUser1() != null && chatRoom.getUser1().getEmail().equals(currentUsername)) {
                response.setMyUnreadCount(chatRoom.getUser1UnreadCount());
                response.setOtherUser(response.getUser2()); // Người còn lại là user2
            } else if (chatRoom.getUser2() != null && chatRoom.getUser2().getEmail().equals(currentUsername)) {
                response.setMyUnreadCount(chatRoom.getUser2UnreadCount());
                response.setOtherUser(response.getUser1()); // Người còn lại là user1
            } else {
                // Trường hợp không xác định được user hiện tại (ví dụ: gọi từ context khác)
                response.setMyUnreadCount(0);
                response.setOtherUser(null);
            }
        } else {
            // Nếu không có user đăng nhập, không tính được
            response.setMyUnreadCount(0);
            response.setOtherUser(null);
        }
        // Xóa thông tin user1, user2 nếu đã có otherUser để response gọn hơn (tùy chọn)
        // response.setUser1(null);
        // response.setUser2(null);
    }
}