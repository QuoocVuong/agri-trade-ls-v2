package com.yourcompany.agritrade.interaction.mapper;

import com.yourcompany.agritrade.interaction.domain.ChatRoom;
import com.yourcompany.agritrade.interaction.dto.response.ChatRoomResponse;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper; // Import UserMapper
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
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

    // ****** INJECT UserRepository ĐỂ LẤY currentUserId ******
    @Autowired
    protected UserRepository userRepository;
    // ****************************************************

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = getCurrentUserId(authentication); // Gọi hàm helper

        if (currentUserId != null) {
            // Tính myUnreadCount và otherUser dựa trên UserInfoSimpleResponse đã được map (bao gồm isOnline)
            if (response.getUser1() != null && response.getUser1().getId().equals(currentUserId)) {
                response.setMyUnreadCount(chatRoom.getUser1UnreadCount()); // Lấy unread count từ entity
                response.setOtherUser(response.getUser2());
            } else if (response.getUser2() != null && response.getUser2().getId().equals(currentUserId)) {
                response.setMyUnreadCount(chatRoom.getUser2UnreadCount()); // Lấy unread count từ entity
                response.setOtherUser(response.getUser1());
            } else {
                response.setMyUnreadCount(0);
                response.setOtherUser(null);
            }
        } else {
            response.setMyUnreadCount(0);
            response.setOtherUser(null);
        }
    }

    // Hàm helper để lấy currentUserId (giờ đã có UserRepository)
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) return null;
        String email = authentication.getName();
        // Dùng UserRepository đã inject
        return userRepository.findByEmail(email).map(User::getId).orElse(null);
    }
}