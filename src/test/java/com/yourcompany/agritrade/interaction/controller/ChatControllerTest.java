package com.yourcompany.agritrade.interaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.interaction.dto.response.ChatRoomResponse;
import com.yourcompany.agritrade.interaction.service.ChatService;
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(TestSecurityConfig.class)
@WithMockUser // Tất cả API trong controller này yêu cầu xác thực
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    // Authentication sẽ được cung cấp bởi @WithMockUser

    private ChatRoomResponse chatRoomResponse;
    private List<ChatRoomResponse> chatRoomResponseList;
    private ChatMessageResponse chatMessageResponse;
    private Page<ChatMessageResponse> chatMessageResponsePage;

    @BeforeEach
    void setUp() {
        UserInfoSimpleResponse user1Info = new UserInfoSimpleResponse();
        user1Info.setId(1L);
        user1Info.setFullName("User One");

        UserInfoSimpleResponse user2Info = new UserInfoSimpleResponse();
        user2Info.setId(2L);
        user2Info.setFullName("User Two");

        chatMessageResponse = new ChatMessageResponse();
        chatMessageResponse.setId(100L);
        chatMessageResponse.setContent("Hello there!");
        chatMessageResponse.setSender(user1Info);
        chatMessageResponse.setRecipient(user2Info);
        chatMessageResponse.setSentAt(LocalDateTime.now());

        chatRoomResponse = new ChatRoomResponse();
        chatRoomResponse.setId(10L);
        chatRoomResponse.setUser1(user1Info);
        chatRoomResponse.setUser2(user2Info);
        chatRoomResponse.setLastMessage(chatMessageResponse);
        chatRoomResponse.setMyUnreadCount(2);

        chatRoomResponseList = List.of(chatRoomResponse);
        chatMessageResponsePage = new PageImpl<>(List.of(chatMessageResponse));
    }

    @Nested
    @DisplayName("Kiểm tra API Phòng Chat")
    class ChatRoomApiTests {
        @Test
        @DisplayName("GET /api/chat/rooms - Lấy Phòng Chat của Tôi - Thành công")
        void getMyChatRooms_success() throws Exception {
            when(chatService.getMyChatRooms(any(Authentication.class))).thenReturn(chatRoomResponseList);

            mockMvc.perform(get("/api/chat/rooms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id", is(chatRoomResponse.getId().intValue())));
        }

        @Test
        @DisplayName("POST /api/chat/rooms/user/{recipientId} - Lấy hoặc Tạo Phòng Chat - Thành công")
        void getOrCreateChatRoom_success() throws Exception {
            Long recipientId = 2L;
            when(chatService.getOrCreateChatRoom(any(Authentication.class), eq(recipientId)))
                    .thenReturn(chatRoomResponse);

            mockMvc.perform(post("/api/chat/rooms/user/{recipientId}", recipientId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is(chatRoomResponse.getId().intValue())));
        }

        @Test
        @DisplayName("POST /api/chat/rooms/user/{recipientId} - Người nhận không tồn tại")
        void getOrCreateChatRoom_recipientNotFound_throwsNotFound() throws Exception {
            Long recipientId = 99L;
            when(chatService.getOrCreateChatRoom(any(Authentication.class), eq(recipientId)))
                    .thenThrow(new ResourceNotFoundException("User", "id", recipientId));

            mockMvc.perform(post("/api/chat/rooms/user/{recipientId}", recipientId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("User not found with id : '99'")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra API Tin nhắn Chat")
    class ChatMessageApiTests {
        @Test
        @DisplayName("GET /api/chat/rooms/{roomId}/messages - Lấy Tin nhắn Chat - Thành công")
        void getChatMessages_success() throws Exception {
            Long roomId = 10L;
            when(chatService.getChatMessages(any(Authentication.class), eq(roomId), any(Pageable.class)))
                    .thenReturn(chatMessageResponsePage);

            mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                            .param("page", "0")
                            .param("size", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id", is(chatMessageResponse.getId().intValue())));
        }

        @Test
        @DisplayName("GET /api/chat/rooms/{roomId}/messages - Phòng chat không tồn tại")
        void getChatMessages_roomNotFound_throwsNotFound() throws Exception {
            Long roomId = 99L;
            when(chatService.getChatMessages(any(Authentication.class), eq(roomId), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("Chat Room", "id", roomId));

            mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Chat Room not found with id : '99'")));
        }

        @Test
        @DisplayName("POST /api/chat/rooms/{roomId}/read - Đánh dấu Đã đọc - Thành công")
        void markMessagesAsRead_success() throws Exception {
            Long roomId = 10L;
            doNothing().when(chatService).markMessagesAsRead(any(Authentication.class), eq(roomId));

            mockMvc.perform(post("/api/chat/rooms/{roomId}/read", roomId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Messages marked as read")));
        }

        @Test
        @DisplayName("POST /api/chat/rooms/{roomId}/read - Phòng chat không tồn tại")
        void markMessagesAsRead_roomNotFound_throwsNotFound() throws Exception {
            Long roomId = 99L;
            doThrow(new ResourceNotFoundException("Chat Room", "id", roomId))
                    .when(chatService).markMessagesAsRead(any(Authentication.class), eq(roomId));

            mockMvc.perform(post("/api/chat/rooms/{roomId}/read", roomId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Chat Room not found with id : '99'")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra API Số lượng Tin nhắn Chưa đọc")
    class UnreadCountApiTests {
        @Test
        @DisplayName("GET /api/chat/unread-count - Thành công")
        void getTotalUnreadMessages_success() throws Exception {
            when(chatService.getTotalUnreadMessages(any(Authentication.class))).thenReturn(5);

            mockMvc.perform(get("/api/chat/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data", is(5)));
        }
    }
}
