package com.yourcompany.agritrade.interaction.dto.request;

import com.yourcompany.agritrade.interaction.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageRequest {

  // ID của người nhận (bắt buộc khi gửi tin nhắn mới qua API/WebSocket)
  @NotNull(message = "Recipient ID is required")
  private Long recipientId;

  // thêm roomId nếu client biết trước phòng chat
  // private Long roomId;

  @NotBlank(message = "Message content cannot be blank")
  @Size(max = 2000, message = "Message content is too long") // Giới hạn độ dài tin nhắn
  private String content;

  // Client có thể chỉ định loại tin nhắn (mặc định là TEXT)
  private MessageType messageType = MessageType.TEXT;

  // Thêm các trường cho ngữ cảnh sản phẩm
  private Long contextProductId;
  private String contextProductName;
  private String contextProductSlug;

   private String contextProductThumbnailUrl;
}
