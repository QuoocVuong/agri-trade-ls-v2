package com.yourcompany.agritrade.interaction.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // Không hiện lastMessage nếu null
public class ChatRoomResponse {
  private Long id;
  private UserInfoSimpleResponse user1; // Thông tin user 1
  private UserInfoSimpleResponse user2; // Thông tin user 2
  private ChatMessageResponse lastMessage; // Tin nhắn cuối cùng (có thể null)
  private LocalDateTime lastMessageTime;
  private int user1UnreadCount;
  private int user2UnreadCount;
  private int
      myUnreadCount; // Số tin nhắn chưa đọc của user đang xem list này (cần tính trong service)
  private UserInfoSimpleResponse
      otherUser; // Thông tin của người còn lại trong phòng chat (cần tính trong service)
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
