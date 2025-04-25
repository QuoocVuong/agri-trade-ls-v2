package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.interaction.domain.ChatMessage;
import com.yourcompany.agritrade.interaction.domain.ChatRoom;
import com.yourcompany.agritrade.interaction.dto.event.MessageReadEvent;
import com.yourcompany.agritrade.interaction.dto.request.ChatMessageRequest;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.interaction.dto.response.ChatRoomResponse;
import com.yourcompany.agritrade.interaction.mapper.ChatMessageMapper;
import com.yourcompany.agritrade.interaction.mapper.ChatRoomMapper;
import com.yourcompany.agritrade.interaction.repository.ChatMessageRepository;
import com.yourcompany.agritrade.interaction.repository.ChatRoomRepository;
import com.yourcompany.agritrade.interaction.service.ChatService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate; // Import để gửi WebSocket message
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatRoomMapper chatRoomMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate; // Inject để gửi qua WebSocket

    @Override
    @Transactional
    public ChatRoomResponse getOrCreateChatRoom(Authentication authentication, Long recipientId) {
        User sender = getUserFromAuthentication(authentication);
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", recipientId));

        // Tìm hoặc tạo phòng chat
        ChatRoom room = chatRoomRepository.findRoomBetweenUsers(sender.getId(), recipient.getId())
                .orElseGet(() -> {
                    log.info("Creating new chat room between user {} and {}", sender.getId(), recipient.getId());
                    return chatRoomRepository.save(new ChatRoom(sender, recipient));
                });

        return chatRoomMapper.toChatRoomResponse(room); // Mapper sẽ tính unread count và other user
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Authentication authentication, ChatMessageRequest request) {
        User sender = getUserFromAuthentication(authentication);
        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient User", "id", request.getRecipientId()));

        // Không cho gửi tin nhắn cho chính mình
        if (sender.getId().equals(recipient.getId())) {
            throw new BadRequestException("Cannot send message to yourself.");
        }


        // Lấy hoặc tạo phòng chat
        ChatRoom room = chatRoomRepository.findRoomBetweenUsers(sender.getId(), recipient.getId())
                .orElseGet(() -> chatRoomRepository.save(new ChatRoom(sender, recipient)));

        // Tạo và lưu tin nhắn
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(request.getContent());
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
         String senderDestination = "/user/" + sender.getEmail() + "/queue/messages"; // Gửi vào cùng queue
         messagingTemplate.convertAndSend(senderDestination, responseDto);
        log.info("Sent WebSocket message back to sender {}: {}", senderDestination, responseDto.getId());

        return responseDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        List<ChatRoom> rooms = chatRoomRepository.findRoomsByUser(user.getId());
        return chatRoomMapper.toChatRoomResponseList(rooms); // Mapper sẽ tính myUnreadCount và otherUser
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getChatMessages(Authentication authentication, Long roomId, Pageable pageable) {
        User user = getUserFromAuthentication(authentication);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat Room", "id", roomId));

        // Kiểm tra xem user có thuộc phòng chat này không
        if (!room.getUser1().getId().equals(user.getId()) && !room.getUser2().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not belong to this chat room");
        }

        Page<ChatMessage> messagePage = chatMessageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable);
        return messagePage.map(chatMessageMapper::toChatMessageResponse);
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Authentication authentication, Long roomId) {
        User user = getUserFromAuthentication(authentication); // Người đọc
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat Room", "id", roomId));

        // Kiểm tra xem user có thuộc phòng chat này không
        if (!room.getUser1().getId().equals(user.getId()) && !room.getUser2().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not belong to this chat room");
        }

        // Đánh dấu tin nhắn là đã đọc trong DB
        int updatedCount = chatMessageRepository.markMessagesAsRead(roomId, user.getId(), LocalDateTime.now());

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
                log.info("Marked {} messages as read for user {} in room {}", updatedCount, user.getId(), roomId);

                // *** Gửi thông báo đã đọc qua WebSocket cho người kia ***
                User otherUser = room.getUser1().getId().equals(user.getId()) ? room.getUser2() : room.getUser1();
                String readNotificationDestination = "/user/" + otherUser.getEmail() + "/queue/read"; // Queue riêng cho thông báo đọc
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
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            // Ném lỗi vì các API chat yêu cầu đăng nhập
            throw new AccessDeniedException("User is not authenticated for chat operations.");
        }
        String email = authentication.getName(); // Lấy email/username từ Principal
        return userRepository.findByEmail(email) // Tìm trong DB
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user not found with email: " + email)); // Ném lỗi nếu không thấy user trong DB
    }
}