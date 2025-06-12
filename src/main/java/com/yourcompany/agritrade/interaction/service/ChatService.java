package com.yourcompany.agritrade.interaction.service;

import com.yourcompany.agritrade.interaction.dto.request.ChatMessageRequest;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.interaction.dto.response.ChatRoomResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface ChatService {

  /** Lấy hoặc tạo phòng chat giữa user hiện tại và người nhận */
  ChatRoomResponse getOrCreateChatRoom(Authentication authentication, Long recipientId);

  /** Gửi tin nhắn mới (có thể dùng cho cả API và WebSocket) */
  ChatMessageResponse sendMessage(Authentication authentication, ChatMessageRequest request);

  /** Lấy danh sách các phòng chat của user hiện tại (phân trang hoặc không) */
  List<ChatRoomResponse> getMyChatRooms(Authentication authentication);

  /** Lấy lịch sử tin nhắn của một phòng chat (phân trang) */
  Page<ChatMessageResponse> getChatMessages(
      Authentication authentication, Long roomId, Pageable pageable);

  /** Đánh dấu tin nhắn trong phòng là đã đọc */
  void markMessagesAsRead(Authentication authentication, Long roomId);

  /** Lấy tổng số tin nhắn chưa đọc của user hiện tại */
  int getTotalUnreadMessages(Authentication authentication);
}
