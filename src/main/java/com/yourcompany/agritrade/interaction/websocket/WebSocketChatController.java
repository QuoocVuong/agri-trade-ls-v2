package com.yourcompany.agritrade.interaction.websocket;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.interaction.dto.event.WebSocketErrorEvent;
import com.yourcompany.agritrade.interaction.dto.request.ChatMessageRequest;
import com.yourcompany.agritrade.interaction.service.ChatService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {

  private final ChatService chatService;
  private final SimpMessagingTemplate messagingTemplate;


  @MessageMapping("/chat.sendMessage")
  public void sendMessage(
      @Payload ChatMessageRequest messageRequest, SimpMessageHeaderAccessor headerAccessor) {
    // Lấy Authentication từ Principal
    Authentication authentication = getAuthentication(headerAccessor);
    log.info(
        "WS Received: /chat.sendMessage from {}: {}",
        authentication.getName(),
        messageRequest.getContent());
    // Gọi service để xử lý (service sẽ tự gửi đến người nhận)
    chatService.sendMessage(authentication, messageRequest);
  }


  @MessageMapping("/chat.markRead")
  public void markMessagesAsRead(@Payload Long roomId, SimpMessageHeaderAccessor headerAccessor) {
    Authentication authentication = getAuthentication(headerAccessor);
    log.info("WS Received: /chat.markRead from {} for room {}", authentication.getName(), roomId);
    chatService.markMessagesAsRead(authentication, roomId);
  }


  // Xử lý lỗi WebSocket tập trung
  @MessageExceptionHandler({
    BadRequestException.class,
    ResourceNotFoundException.class,
    AccessDeniedException.class,
    Exception.class
  })
  @SendToUser("/queue/errors") // Gửi lỗi về private queue "/user/queue/errors" của người gửi
  public WebSocketErrorEvent handleException(Exception exception, Principal principal) {
    String username = (principal != null) ? principal.getName() : "Unknown User";
    log.error("WebSocket Error for user {}: {}", username, exception.getMessage());

    String errorType = exception.getClass().getSimpleName();
    String errorMessage = exception.getMessage();

    // Có thể tùy chỉnh message dựa trên loại exception
    if (exception instanceof AccessDeniedException) {
      errorMessage = "Permission denied.";
    } else if (!(exception instanceof BadRequestException
        || exception instanceof ResourceNotFoundException)) {
      // Che giấu lỗi server nội bộ
      errorMessage = "An error occurred while processing your request.";
    }

    return new WebSocketErrorEvent(errorType, errorMessage);
  }

  // Helper lấy Authentication an toàn
  public Authentication getAuthentication(SimpMessageHeaderAccessor headerAccessor) {
    Principal principal = headerAccessor.getUser();
    if (principal instanceof Authentication) {
      return (Authentication) principal;
    }
    log.error(
        "Cannot get Authentication from WebSocket session: {}", headerAccessor.getSessionId());
    throw new AccessDeniedException("User not properly authenticated in WebSocket session.");
  }
}
