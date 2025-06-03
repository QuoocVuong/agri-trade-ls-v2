package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
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
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatRoomMapper chatRoomMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private Authentication authentication;

    @InjectMocks
    private ChatServiceImpl chatService;

    private User senderUser, recipientUser;
    private ChatRoom chatRoomEntity;
    private ChatRoomResponse chatRoomResponseDto;
    private ChatMessage chatMessageEntity;
    private ChatMessageResponse chatMessageResponseDto;
    private ChatMessageRequest chatMessageRequest;

    @BeforeEach
    void setUp() {
        senderUser = new User();
        senderUser.setId(1L);
        senderUser.setEmail("sender@example.com");
        senderUser.setFullName("Sender User");

        recipientUser = new User();
        recipientUser.setId(2L);
        recipientUser.setEmail("recipient@example.com");
        recipientUser.setFullName("Recipient User");

        chatRoomEntity = new ChatRoom(senderUser, recipientUser); // Constructor đảm bảo user1.id < user2.id
        chatRoomEntity.setId(10L);
        chatRoomEntity.setUser1UnreadCount(0);
        chatRoomEntity.setUser2UnreadCount(0);


        chatRoomResponseDto = new ChatRoomResponse();
        chatRoomResponseDto.setId(10L);
        // ... các trường khác của DTO

        chatMessageRequest = new ChatMessageRequest();
        chatMessageRequest.setRecipientId(recipientUser.getId());
        chatMessageRequest.setContent("Hello!");
        chatMessageRequest.setMessageType(MessageType.TEXT);

        chatMessageEntity = new ChatMessage();
        chatMessageEntity.setId(100L);
        chatMessageEntity.setRoom(chatRoomEntity);
        chatMessageEntity.setSender(senderUser);
        chatMessageEntity.setRecipient(recipientUser);
        chatMessageEntity.setContent("Hello!");
        chatMessageEntity.setSentAt(LocalDateTime.now());

        chatMessageResponseDto = new ChatMessageResponse();
        chatMessageResponseDto.setId(100L);
        chatMessageResponseDto.setContent("Hello!");
        // ...

        lenient().when(authentication.getName()).thenReturn(senderUser.getEmail());
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(userRepository.findByEmail(senderUser.getEmail())).thenReturn(Optional.of(senderUser));
    }

    private void mockAuthenticatedUser(User user) {
        when(authentication.getName()).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("Get Or Create Chat Room Tests")
    class GetOrCreateChatRoomTests {
        @Test
        @DisplayName("Get Or Create - Room Exists")
        void getOrCreateChatRoom_whenRoomExists_shouldReturnExistingRoom() {
            mockAuthenticatedUser(senderUser);
            when(userRepository.findById(recipientUser.getId())).thenReturn(Optional.of(recipientUser));
            when(chatRoomRepository.findRoomBetweenUsers(senderUser.getId(), recipientUser.getId()))
                    .thenReturn(Optional.of(chatRoomEntity));
            when(chatRoomMapper.toChatRoomResponse(chatRoomEntity)).thenReturn(chatRoomResponseDto);

            ChatRoomResponse result = chatService.getOrCreateChatRoom(authentication, recipientUser.getId());

            assertNotNull(result);
            assertEquals(chatRoomResponseDto.getId(), result.getId());
            verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        }

        @Test
        @DisplayName("Get Or Create - Room Not Exists - Creates New Room")
        void getOrCreateChatRoom_whenRoomNotExists_shouldCreateNewRoom() {
            mockAuthenticatedUser(senderUser);
            when(userRepository.findById(recipientUser.getId())).thenReturn(Optional.of(recipientUser));
            when(chatRoomRepository.findRoomBetweenUsers(senderUser.getId(), recipientUser.getId()))
                    .thenReturn(Optional.empty());
            when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(chatRoomEntity); // Trả về room đã lưu
            when(chatRoomMapper.toChatRoomResponse(chatRoomEntity)).thenReturn(chatRoomResponseDto);

            ChatRoomResponse result = chatService.getOrCreateChatRoom(authentication, recipientUser.getId());

            assertNotNull(result);
            verify(chatRoomRepository).save(any(ChatRoom.class));
        }

        @Test
        @DisplayName("Get Or Create - Recipient Not Found - Throws ResourceNotFoundException")
        void getOrCreateChatRoom_whenRecipientNotFound_shouldThrowResourceNotFound() {
            mockAuthenticatedUser(senderUser);
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> chatService.getOrCreateChatRoom(authentication, 99L));
        }
    }

    @Nested
    @DisplayName("Send Message Tests")
    class SendMessageTests {
        @Test
        @DisplayName("Send Message - Success - Room Exists")
        void sendMessage_success_roomExists() {
            mockAuthenticatedUser(senderUser);
            when(userRepository.findById(recipientUser.getId())).thenReturn(Optional.of(recipientUser));
            when(chatRoomRepository.findRoomBetweenUsers(senderUser.getId(), recipientUser.getId()))
                    .thenReturn(Optional.of(chatRoomEntity)); // Phòng đã tồn tại
            when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessageEntity);
            when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(chatRoomEntity); // Lưu lại room với lastMessage
            when(chatMessageMapper.toChatMessageResponse(chatMessageEntity)).thenReturn(chatMessageResponseDto);
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(ChatMessageResponse.class));

            ChatMessageResponse result = chatService.sendMessage(authentication, chatMessageRequest);

            assertNotNull(result);
            assertEquals(chatMessageResponseDto.getContent(), result.getContent());

            ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(msgCaptor.capture());
            assertEquals(senderUser, msgCaptor.getValue().getSender());
            assertEquals(recipientUser, msgCaptor.getValue().getRecipient());

            ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
            verify(chatRoomRepository).save(roomCaptor.capture());
            assertEquals(chatMessageEntity, roomCaptor.getValue().getLastMessage());
            assertEquals(1, roomCaptor.getValue().getUser2UnreadCount()); // recipientUser là user2

            verify(messagingTemplate, times(2)).convertAndSend(anyString(), eq(chatMessageResponseDto));
        }

        @Test
        @DisplayName("Send Message - Success - Creates New Room")
        void sendMessage_success_createsNewRoom() {
            mockAuthenticatedUser(senderUser);
            when(userRepository.findById(recipientUser.getId())).thenReturn(Optional.of(recipientUser));
            when(chatRoomRepository.findRoomBetweenUsers(senderUser.getId(), recipientUser.getId()))
                    .thenReturn(Optional.empty()); // Phòng chưa tồn tại
            when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(chatRoomEntity); // Lần đầu save room mới, lần sau save room với lastMessage
            when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessageEntity);
            when(chatMessageMapper.toChatMessageResponse(chatMessageEntity)).thenReturn(chatMessageResponseDto);

            chatService.sendMessage(authentication, chatMessageRequest);

            verify(chatRoomRepository, times(2)).save(any(ChatRoom.class)); // 1 cho tạo mới, 1 cho cập nhật lastMessage
            verify(messagingTemplate, times(2)).convertAndSend(anyString(), eq(chatMessageResponseDto));
        }


        @Test
        @DisplayName("Send Message - To Self - Throws BadRequestException")
        void sendMessage_toSelf_throwsBadRequest() {
            mockAuthenticatedUser(senderUser);
            chatMessageRequest.setRecipientId(senderUser.getId()); // Gửi cho chính mình
            when(userRepository.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));

            assertThrows(BadRequestException.class,
                    () -> chatService.sendMessage(authentication, chatMessageRequest));
        }
    }

    @Nested
    @DisplayName("Get Messages and Rooms Tests")
    class GetMessagesAndRoomsTests {
        @Test
        @DisplayName("Get My Chat Rooms - Success")
        void getMyChatRooms_success() {
            mockAuthenticatedUser(senderUser);
            when(chatRoomRepository.findRoomsByUser(senderUser.getId())).thenReturn(List.of(chatRoomEntity));
            when(chatRoomMapper.toChatRoomResponseList(List.of(chatRoomEntity))).thenReturn(List.of(chatRoomResponseDto));

            List<ChatRoomResponse> result = chatService.getMyChatRooms(authentication);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Get Chat Messages - Success")
        void getChatMessages_success() {
            mockAuthenticatedUser(senderUser); // senderUser là user1 trong chatRoomEntity
            Pageable pageable = PageRequest.of(0, 10);
            Page<ChatMessage> messagePage = new PageImpl<>(List.of(chatMessageEntity), pageable, 1);

            when(chatRoomRepository.findById(chatRoomEntity.getId())).thenReturn(Optional.of(chatRoomEntity));
            when(chatMessageRepository.findByRoomIdOrderBySentAtDesc(chatRoomEntity.getId(), pageable))
                    .thenReturn(messagePage);
            when(chatMessageMapper.toChatMessageResponse(chatMessageEntity)).thenReturn(chatMessageResponseDto); // Cho map()

            Page<ChatMessageResponse> result = chatService.getChatMessages(authentication, chatRoomEntity.getId(), pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Get Chat Messages - Room Not Found - Throws ResourceNotFoundException")
        void getChatMessages_roomNotFound_throwsResourceNotFound() {
            mockAuthenticatedUser(senderUser);
            when(chatRoomRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> chatService.getChatMessages(authentication, 99L, PageRequest.of(0,10)));
        }

        @Test
        @DisplayName("Get Chat Messages - User Not in Room - Throws AccessDeniedException")
        void getChatMessages_userNotInRoom_throwsAccessDenied() {
            User anotherUser = new User(); anotherUser.setId(3L); anotherUser.setEmail("another@example.com");
            mockAuthenticatedUser(anotherUser); // User này không thuộc chatRoomEntity
            when(chatRoomRepository.findById(chatRoomEntity.getId())).thenReturn(Optional.of(chatRoomEntity));

            assertThrows(AccessDeniedException.class,
                    () -> chatService.getChatMessages(authentication, chatRoomEntity.getId(), PageRequest.of(0,10)));
        }
    }

    @Nested
    @DisplayName("Mark Messages As Read Tests")
    class MarkMessagesAsReadTests {
        @Test
        @DisplayName("Mark Messages As Read - Success for User2")
        void markMessagesAsRead_success_forUser2() {
            mockAuthenticatedUser(recipientUser); // recipientUser (user2) đọc tin nhắn
            chatRoomEntity.setUser2UnreadCount(5); // Có 5 tin nhắn chưa đọc

            when(chatRoomRepository.findById(chatRoomEntity.getId())).thenReturn(Optional.of(chatRoomEntity));
            when(chatMessageRepository.markMessagesAsRead(eq(chatRoomEntity.getId()), eq(recipientUser.getId()), any(LocalDateTime.class)))
                    .thenReturn(5); // 5 tin nhắn được cập nhật
            when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(chatRoomEntity);
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(MessageReadEvent.class));

            chatService.markMessagesAsRead(authentication, chatRoomEntity.getId());

            assertEquals(0, chatRoomEntity.getUser2UnreadCount());
            verify(chatRoomRepository).save(chatRoomEntity);
            verify(messagingTemplate).convertAndSend(eq("/user/" + senderUser.getEmail() + "/queue/read"), any(MessageReadEvent.class));
        }

        @Test
        @DisplayName("Mark Messages As Read - No Messages Updated - Does Not Send WebSocket")
        void markMessagesAsRead_noMessagesUpdated_doesNotSendWebSocket() {
            mockAuthenticatedUser(senderUser);
            chatRoomEntity.setUser1UnreadCount(0); // Không có tin nhắn chưa đọc

            when(chatRoomRepository.findById(chatRoomEntity.getId())).thenReturn(Optional.of(chatRoomEntity));
            when(chatMessageRepository.markMessagesAsRead(eq(chatRoomEntity.getId()), eq(senderUser.getId()), any(LocalDateTime.class)))
                    .thenReturn(0); // 0 tin nhắn được cập nhật

            chatService.markMessagesAsRead(authentication, chatRoomEntity.getId());

            verify(chatRoomRepository, never()).save(any(ChatRoom.class));
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(MessageReadEvent.class));
        }
    }

    @Test
    @DisplayName("Get Total Unread Messages - Success")
    void getTotalUnreadMessages_success() {
        mockAuthenticatedUser(senderUser);
        when(chatRoomRepository.getTotalUnreadCountForUser(senderUser.getId())).thenReturn(Optional.of(7));
        int count = chatService.getTotalUnreadMessages(authentication);
        assertEquals(7, count);
    }

    @Test
    @DisplayName("Get Total Unread Messages - No Unread")
    void getTotalUnreadMessages_noUnread() {
        mockAuthenticatedUser(senderUser);
        when(chatRoomRepository.getTotalUnreadCountForUser(senderUser.getId())).thenReturn(Optional.of(0));
        int count = chatService.getTotalUnreadMessages(authentication);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("Get User From Authentication - Not Authenticated - Throws AccessDeniedException")
    void getUserFromAuthentication_whenNotAuthenticated_shouldThrowAccessDeniedException() {
        when(authentication.isAuthenticated()).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> chatService.getMyChatRooms(authentication)); // Gọi một hàm bất kỳ cần user
    }
}