package com.yourcompany.agritrade.interaction.dto.response;

import com.yourcompany.agritrade.interaction.domain.MessageType;
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse; // DTO user đơn giản
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {
    private Long id;
    private Long roomId; // ID phòng chat
    private UserInfoSimpleResponse sender; // Thông tin người gửi (đơn giản)
    private UserInfoSimpleResponse recipient; // Thông tin người nhận (đơn giản)
    private String content;
    private MessageType messageType;
    private LocalDateTime sentAt;
    private boolean isRead;
    private LocalDateTime readAt;
}