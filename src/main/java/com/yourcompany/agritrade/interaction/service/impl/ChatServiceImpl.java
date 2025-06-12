package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.interaction.domain.ChatMessage;
import com.yourcompany.agritrade.interaction.domain.ChatRoom;
import com.yourcompany.agritrade.interaction.domain.MessageType;
import com.yourcompany.agritrade.interaction.dto.event.MessageReadEvent;
import com.yourcompany.agritrade.interaction.dto.request.ChatMessageRequest;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.interaction.dto.response.ChatRoomResponse;
import com.yourcompany.agritrade.interaction.mapper.ChatMessageMapper;
import com.yourcompany.agritrade.interaction.mapper.ChatRoomMapper;
import com.yourcompany.agritrade.interaction.repository.ChatMessageRepository;
import com.yourcompany.agritrade.interaction.repository.ChatRoomRepository;
import com.yourcompany.agritrade.interaction.service.ChatService;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import com.yourcompany.agritrade.ordering.repository.SupplyOrderRequestRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final UserRepository userRepository;
  private final ChatRoomMapper chatRoomMapper;
  private final ChatMessageMapper chatMessageMapper;
  private final SimpMessagingTemplate messagingTemplate;
  private final SupplyOrderRequestRepository supplyOrderRequestRepository;

  @Value("${app.frontend.url}") // Lấy URL frontend
  private String frontendUrl;

  @Override
  @Transactional
  public ChatRoomResponse getOrCreateChatRoom(Authentication authentication, Long recipientId) {
    User sender = getUserFromAuthentication(authentication);
    User recipient =
        userRepository
            .findById(recipientId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", recipientId));

    // Tìm hoặc tạo phòng chat
    ChatRoom room =
        chatRoomRepository
            .findRoomBetweenUsers(sender.getId(), recipient.getId())
            .orElseGet(
                () -> {
                  log.info(
                      "Creating new chat room between user {} and {}",
                      sender.getId(),
                      recipient.getId());
                  return chatRoomRepository.save(new ChatRoom(sender, recipient));
                });

    return chatRoomMapper.toChatRoomResponse(room); // Mapper sẽ tính unread count và other user
  }

  @Override
  @Transactional
  public ChatMessageResponse sendMessage(
      Authentication authentication, ChatMessageRequest request) {
    User sender = getUserFromAuthentication(authentication);
    User recipient =
        userRepository
            .findById(request.getRecipientId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Recipient User", "id", request.getRecipientId()));

    // Không cho gửi tin nhắn cho chính mình
    if (sender.getId().equals(recipient.getId())) {
      throw new BadRequestException("Cannot send message to yourself.");
    }

    // Lấy hoặc tạo phòng chat
    ChatRoom room =
        chatRoomRepository
            .findRoomBetweenUsers(sender.getId(), recipient.getId())
            .orElseGet(() -> chatRoomRepository.save(new ChatRoom(sender, recipient)));

    // Logic cập nhật trạng thái SupplyRequest khi Farmer bắt đầu chat
    if (request.getContextProductId() != null
        && sender.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
      // Cần inject SupplyOrderRequestRepository vào ChatServiceImpl
      supplyOrderRequestRepository
          .findByFarmerIdAndBuyerIdAndProductIdAndStatus(
              sender.getId(),
              recipient.getId(),
              request.getContextProductId(),
              SupplyOrderRequestStatus.PENDING_FARMER_ACTION)
          .forEach(
              req -> {
                req.setStatus(SupplyOrderRequestStatus.NEGOTIATING);
                supplyOrderRequestRepository.save(req);
                log.info(
                    "SupplyOrderRequest {} status updated to NEGOTIATING due to chat initiation.",
                    req.getId());
                // (Tùy chọn) Gửi thông báo cập nhật trạng thái qua WebSocket cho cả hai bên
              });
    }

    boolean shouldSendContextMessage =
        request.getContextProductId() != null
            && request.getContextProductName() != null
            && !hasRecentContextMessage(
                room.getId(), request.getContextProductId(), sender.getId());

    if (shouldSendContextMessage) {
      String productLink = "#"; // Link mặc định
      if (request.getContextProductSlug() != null) {

        productLink = frontendUrl + "/supply-sources/detail/" + request.getContextProductSlug();
      }

      String contextMessageContent =
          String.format(
              "Tôi quan tâm đến sản phẩm: %s (ID: %d).",
              request.getContextProductName(),
              request.getContextProductId(),
              String.format(
                  "Thông tin sản phẩm đang quan tâm: <a href='%s' target='_blank'>%s</a> (ID: %d)",
                  productLink, request.getContextProductName(), request.getContextProductId()));

      ChatMessage contextMessage = new ChatMessage();
      contextMessage.setRoom(room);
      contextMessage.setSender(sender); // Người gửi vẫn là người dùng hiện tại
      contextMessage.setRecipient(recipient);
      contextMessage.setContent(contextMessageContent);
      contextMessage.setMessageType(MessageType.SYSTEM); // Đánh dấu là tin nhắn hệ thống/ngữ cảnh
      contextMessage.setSentAt(
          LocalDateTime.now().minusNanos(1000000)); // Gửi trước tin nhắn chính một chút
      contextMessage.setRead(false); // Ban đầu chưa đọc
      ChatMessage savedContextMessage = chatMessageRepository.save(contextMessage);

      // Gửi tin nhắn ngữ cảnh qua WebSocket cho cả hai bên
      ChatMessageResponse contextDto = chatMessageMapper.toChatMessageResponse(savedContextMessage);
      String recipientContextDest = "/user/" + recipient.getEmail() + "/queue/messages";
      messagingTemplate.convertAndSend(recipientContextDest, contextDto);
      String senderContextDest =
          "/user/" + sender.getEmail() + "/queue/messages"; // Gửi lại cho người gửi để họ cũng thấy
      messagingTemplate.convertAndSend(senderContextDest, contextDto);
      log.info(
          "Sent SYSTEM context product message for product ID {} to room {}",
          request.getContextProductId(),
          room.getId());

      // Cập nhật lastMessage của phòng nếu đây là tin nhắn đầu tiên
      // (Tin nhắn người dùng sẽ ghi đè sau)
      if (room.getLastMessage() == null) {
        room.setLastMessage(savedContextMessage);
        room.setLastMessageTime(savedContextMessage.getSentAt());
        // Không tăng unread count cho tin nhắn hệ thống này
        chatRoomRepository.save(room);
      }
    }

    // Tạo và lưu tin nhắn
    ChatMessage message = new ChatMessage();
    message.setRoom(room);
    message.setSender(sender);
    message.setRecipient(recipient);
    message.setContent(request.getContent()); // Nội dung người dùng nhập
    message.setMessageType(request.getMessageType());
    message.setSentAt(LocalDateTime.now()); // Gán thời gian gửi
    message.setRead(false); // Tin nhắn mới chưa đọc

    ChatMessage savedMessage = chatMessageRepository.save(message);

    // Cập nhật thông tin phòng chat
    room.setLastMessage(savedMessage);
    room.setLastMessageTime(savedMessage.getSentAt());
    // Tăng unread count cho người nhận
    if (room.getUser1().getId().equals(recipient.getId())) {
      room.setUser1UnreadCount(room.getUser1UnreadCount() + 1);
    } else {
      room.setUser2UnreadCount(room.getUser2UnreadCount() + 1);
    }
    chatRoomRepository.save(room); // Lưu lại phòng chat

    // Map sang response DTO
    ChatMessageResponse responseDto = chatMessageMapper.toChatMessageResponse(savedMessage);

    // *** Gửi tin nhắn qua WebSocket đến người nhận ***
    // Đích đến là private queue của người nhận
    String destination = "/user/" + recipient.getEmail() + "/queue/messages";
    messagingTemplate.convertAndSend(destination, responseDto);
    log.info("Sent WebSocket message to {}: {}", destination, responseDto.getId());

    // (Tùy chọn) Gửi lại cho người gửi để xác nhận đã gửi thành công
    String senderDestination =
        "/user/" + sender.getEmail() + "/queue/messages"; // Gửi vào cùng queue
    messagingTemplate.convertAndSend(senderDestination, responseDto);
    log.info(
        "Sent WebSocket message back to sender {}: {}", senderDestination, responseDto.getId());

    return responseDto;
  }

  // Helper method để kiểm tra xem có tin nhắn context gần đây không
  private boolean hasRecentContextMessage(Long roomId, Long contextProductId, Long senderId) {
    // Tìm 5 tin nhắn cuối cùng trong phòng của sender này
    Pageable recentMessagesPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "sentAt"));
    Page<ChatMessage> recentMessages =
        chatMessageRepository.findByRoomIdAndSenderIdOrderBySentAtDesc(
            roomId, senderId, recentMessagesPageable);

    for (ChatMessage msg : recentMessages.getContent()) {
      if (msg.getMessageType() == MessageType.SYSTEM
          && msg.getContent() != null
          && // Thêm kiểm tra null cho content
          msg.getContent().contains("(ID: " + contextProductId + ")")) { // Kiểm tra nội dung
        if (msg.getSentAt()
            .isAfter(LocalDateTime.now().minusMinutes(1))) { // Giảm thời gian kiểm tra xuống 1 phút
          log.debug(
              "Recent SYSTEM message for product {} in room {} by sender {} found. Skipping duplicate.",
              contextProductId,
              roomId,
              senderId);
          return true;
        }
      }
    }
    return false;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatRoomResponse> getMyChatRooms(Authentication authentication) {
    User user = getUserFromAuthentication(authentication);
    List<ChatRoom> rooms = chatRoomRepository.findRoomsByUser(user.getId());
    return chatRoomMapper.toChatRoomResponseList(
        rooms); // Mapper sẽ tính myUnreadCount và otherUser
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ChatMessageResponse> getChatMessages(
      Authentication authentication, Long roomId, Pageable pageable) {
    User user = getUserFromAuthentication(authentication);
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new ResourceNotFoundException("Chat Room", "id", roomId));

    // Kiểm tra xem user có thuộc phòng chat này không
    if (!room.getUser1().getId().equals(user.getId())
        && !room.getUser2().getId().equals(user.getId())) {
      throw new AccessDeniedException("User does not belong to this chat room");
    }

    Page<ChatMessage> messagePage =
        chatMessageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable);
    return messagePage.map(chatMessageMapper::toChatMessageResponse);
  }

  @Override
  @Transactional
  public void markMessagesAsRead(Authentication authentication, Long roomId) {
    User user = getUserFromAuthentication(authentication); // Người đọc
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new ResourceNotFoundException("Chat Room", "id", roomId));

    // Kiểm tra xem user có thuộc phòng chat này không
    if (!room.getUser1().getId().equals(user.getId())
        && !room.getUser2().getId().equals(user.getId())) {
      throw new AccessDeniedException("User does not belong to this chat room");
    }

    // Đánh dấu tin nhắn là đã đọc trong DB
    int updatedCount =
        chatMessageRepository.markMessagesAsRead(roomId, user.getId(), LocalDateTime.now());

    // Reset unread count của user này trong phòng chat
    // Reset unread count và lưu phòng chat
    if (updatedCount > 0) {
      boolean updated = false;
      if (room.getUser1().getId().equals(user.getId()) && room.getUser1UnreadCount() > 0) {
        room.setUser1UnreadCount(0);
        updated = true;
      } else if (room.getUser2().getId().equals(user.getId()) && room.getUser2UnreadCount() > 0) {
        room.setUser2UnreadCount(0);
        updated = true;
      }
      if (updated) {
        chatRoomRepository.save(room);
        log.info(
            "Marked {} messages as read for user {} in room {}",
            updatedCount,
            user.getId(),
            roomId);

        // Gửi thông báo đã đọc qua WebSocket cho người kia
        User otherUser =
            room.getUser1().getId().equals(user.getId()) ? room.getUser2() : room.getUser1();
        String readNotificationDestination =
            "/user/" + otherUser.getEmail() + "/queue/read"; // Queue riêng cho thông báo đọc
        MessageReadEvent readEvent = new MessageReadEvent(roomId, user.getId()); // Tạo DTO sự kiện
        messagingTemplate.convertAndSend(readNotificationDestination, readEvent);
        log.info("Sent read notification to {} for room {}", otherUser.getEmail(), roomId);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public int getTotalUnreadMessages(Authentication authentication) {
    User user = getUserFromAuthentication(authentication);
    return chatRoomRepository.getTotalUnreadCountForUser(user.getId()).orElse(0);
  }

  // Helper method
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      // Ném lỗi vì các API chat yêu cầu đăng nhập
      throw new AccessDeniedException("User is not authenticated for chat operations.");
    }
    String email = authentication.getName(); // Lấy email/username từ Principal
    return userRepository
        .findByEmail(email) // Tìm trong DB
        .orElseThrow(
            () ->
                new UsernameNotFoundException(
                    "Authenticated user not found with email: "
                        + email)); // Ném lỗi nếu không thấy user trong DB
  }
}
