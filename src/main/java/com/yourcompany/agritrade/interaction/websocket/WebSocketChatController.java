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
  // private final SimpMessagingTemplate messagingTemplate; // Inject nếu cần gửi thêm
  private final SimpMessagingTemplate messagingTemplate;

  //    /**
  //     * Xử lý tin nhắn gửi từ client đến server qua WebSocket.
  //     * Client sẽ gửi đến đích /app/chat.sendMessage (theo cấu hình WebSocketConfig).
  //     * Server sẽ xử lý và gửi lại tin nhắn đã lưu đến private queue của người nhận.
  //     * @param messageRequest DTO chứa nội dung tin nhắn và người nhận.
  //     * @param headerAccessor Chứa thông tin về session WebSocket và Principal (user đã xác
  // thực).
  //     */
  //    @MessageMapping("/chat.sendMessage") // Endpoint nhận tin nhắn từ client
  //    // @SendToUser("/queue/messages") // Không dùng SendToUser trực tiếp ở đây vì cần logic phức
  // tạp hơn
  //    public void sendMessage(@Payload ChatMessageRequest messageRequest,
  //                            SimpMessageHeaderAccessor headerAccessor) {
  //
  //        Principal principal = headerAccessor.getUser(); // Lấy Principal (chứa Authentication)
  //        if (principal == null) {
  //            log.error("Cannot send message: User principal not found in WebSocket session.");
  //            // Có thể gửi lỗi về client nếu cần
  //            return;
  //        }
  //
  //        // Lấy Authentication object từ Principal
  //        Authentication authentication = (Authentication) principal;
  //
  //        log.info("Received chat message via WebSocket from {}: {}", authentication.getName(),
  // messageRequest.getContent());
  //
  //        try {
  //            // Gọi ChatService để lưu và gửi tin nhắn (Service sẽ tự push qua WebSocket đến
  // người nhận)
  //            ChatMessageResponse savedMessage = chatService.sendMessage(authentication,
  // messageRequest);
  //
  //            // (Tùy chọn) Gửi lại tin nhắn đã lưu cho chính người gửi để xác nhận
  //            // String senderDestination = "/user/" + authentication.getName() + "/queue/sent";
  //            // messagingTemplate.convertAndSend(senderDestination, savedMessage);
  //
  //        } catch (Exception e) {
  //            log.error("Error processing WebSocket chat message from {}: {}",
  // authentication.getName(), e.getMessage(), e);
  //            // TODO: Gửi thông báo lỗi về cho người gửi qua WebSocket nếu cần
  //            // String errorDestination = "/user/" + authentication.getName() + "/queue/errors";
  //            // messagingTemplate.convertAndSend(errorDestination, "Error sending message: " +
  // e.getMessage());
  //        }
  //    }

  @MessageMapping("/chat.sendMessage")
  public void sendMessage(
      @Payload ChatMessageRequest messageRequest, SimpMessageHeaderAccessor headerAccessor) {
    // Lấy Authentication từ Principal (đã được thiết lập bởi Security)
    Authentication authentication = getAuthentication(headerAccessor);
    log.info(
        "WS Received: /chat.sendMessage from {}: {}",
        authentication.getName(),
        messageRequest.getContent());
    // Gọi service để xử lý (service sẽ tự gửi đến người nhận)
    // Exception sẽ được bắt bởi @MessageExceptionHandler
    chatService.sendMessage(authentication, messageRequest);
  }

  //    /**
  //     * (Tùy chọn) Xử lý khi client gửi thông báo "đã đọc" qua WebSocket.
  //     * Client gửi đến /app/chat.markRead
  //     * @param roomId ID phòng chat đã đọc.
  //     * @param headerAccessor Header WebSocket.
  //     */
  //    @MessageMapping("/chat.markRead")
  //    public void markMessagesAsRead(@Payload Long roomId, SimpMessageHeaderAccessor
  // headerAccessor) {
  //        Principal principal = headerAccessor.getUser();
  //        if (principal == null) {
  //            log.error("Cannot mark messages as read: User principal not found.");
  //            return;
  //        }
  //        Authentication authentication = (Authentication) principal;
  //        log.info("Received mark read request via WebSocket from {} for room {}",
  // authentication.getName(), roomId);
  //        try {
  //            chatService.markMessagesAsRead(authentication, roomId);
  //            // TODO: Gửi thông báo đã đọc cho người kia trong phòng chat nếu cần
  //        } catch (Exception e) {
  //            log.error("Error processing mark read request from {}: {}",
  // authentication.getName(), e.getMessage(), e);
  //        }
  //    }

  @MessageMapping("/chat.markRead")
  public void markMessagesAsRead(@Payload Long roomId, SimpMessageHeaderAccessor headerAccessor) {
    Authentication authentication = getAuthentication(headerAccessor);
    log.info("WS Received: /chat.markRead from {} for room {}", authentication.getName(), roomId);
    chatService.markMessagesAsRead(authentication, roomId);
  }

  // Có thể thêm các @MessageMapping khác cho các hành động chat khác (typing indicator...)
  // --- Xử lý lỗi WebSocket tập trung ---
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
