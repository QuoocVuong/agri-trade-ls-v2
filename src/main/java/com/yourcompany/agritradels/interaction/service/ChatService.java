package com.yourcompany.agritradels.interaction.service;

import com.yourcompany.agritradels.interaction.dto.request.ChatMessageRequest;
import com.yourcompany.agritradels.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritradels.interaction.dto.response.ChatRoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import java.util.List;

public interface ChatService {

    /** Lấy hoặc tạo phòng chat giữa user hiện tại và người nhận */
    ChatRoomResponse getOrCreateChatRoom(Authentication authentication, Long recipientId);

    /** Gửi tin nhắn mới (có thể dùng cho cả API và WebSocket) */
    ChatMessageResponse sendMessage(Authentication authentication, ChatMessageRequest request);

    /** Lấy danh sách các phòng chat của user hiện tại (phân trang hoặc không) */
    List<ChatRoomResponse> getMyChatRooms(Authentication authentication);
    // Hoặc Page<ChatRoomResponse> getMyChatRooms(Authentication authentication, Pageable pageable);

    /** Lấy lịch sử tin nhắn của một phòng chat (phân trang) */
    Page<ChatMessageResponse> getChatMessages(Authentication authentication, Long roomId, Pageable pageable);

    /** Đánh dấu tin nhắn trong phòng là đã đọc */
    void markMessagesAsRead(Authentication authentication, Long roomId);

    /** Lấy tổng số tin nhắn chưa đọc của user hiện tại */
    int getTotalUnreadMessages(Authentication authentication);
}