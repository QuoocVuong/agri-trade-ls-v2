package com.yourcompany.agritrade.interaction.websocket;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.interaction.dto.event.WebSocketErrorEvent;
import com.yourcompany.agritrade.interaction.dto.request.ChatMessageRequest;
import com.yourcompany.agritrade.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritrade.interaction.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate; // Không cần mock trực tiếp nếu controller không dùng
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;


import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketChatControllerTest {

    @Mock
    private ChatService chatService;
    // SimpMessagingTemplate không được controller này sử dụng trực tiếp để gửi,
    // mà là ChatServiceImpl sử dụng, nên không cần mock ở đây.
    // @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketChatController webSocketChatController;

    private SimpMessageHeaderAccessor headerAccessor;
    private Authentication mockAuthentication;
    private ChatMessageRequest chatMessageRequest;
    private final Long TEST_ROOM_ID = 1L;
    private final String TEST_USER_EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        headerAccessor = SimpMessageHeaderAccessor.create();
        // Tạo một đối tượng Authentication giả lập
        mockAuthentication = new UsernamePasswordAuthenticationToken(TEST_USER_EMAIL, null, Collections.emptyList());
        headerAccessor.setUser(mockAuthentication); // Gán Principal (Authentication) vào header

        chatMessageRequest = new ChatMessageRequest();
        chatMessageRequest.setRecipientId(2L);
        chatMessageRequest.setContent("Hello via WebSocket");
    }

    @Nested
    @DisplayName("sendMessage Tests")
    class SendMessageTests {
        @Test
        @DisplayName("sendMessage - Valid User - Calls ChatService")
        void sendMessage_validUser_callsChatService() {
            // Tạo một đối tượng ChatMessageResponse giả lập để trả về
            ChatMessageResponse mockChatMessageResponse = new ChatMessageResponse();
            mockChatMessageResponse.setId(123L); // Gán một vài giá trị mẫu
            mockChatMessageResponse.setContent(chatMessageRequest.getContent());

            // SỬA DÒNG NÀY:
            // Cũ: doNothing().when(chatService).sendMessage(eq(mockAuthentication), eq(chatMessageRequest));
            // Mới:
            when(chatService.sendMessage(eq(mockAuthentication), eq(chatMessageRequest)))
                    .thenReturn(mockChatMessageResponse); // Trả về đối tượng giả lập

            webSocketChatController.sendMessage(chatMessageRequest, headerAccessor);

            verify(chatService).sendMessage(eq(mockAuthentication), eq(chatMessageRequest));
        }

        @Test
        @DisplayName("sendMessage - User Not Properly Authenticated in Header - Throws AccessDeniedException (caught by handler)")
        void sendMessage_userNotAuthenticatedInHeader_throwsAccessDenied() {
            headerAccessor.setUser(mock(Principal.class)); // Giả lập Principal không phải là Authentication

            // Exception sẽ được bắt bởi @MessageExceptionHandler, nên test này kiểm tra gián tiếp
            // bằng cách đảm bảo chatService.sendMessage không được gọi.
            // Hoặc, nếu muốn test trực tiếp getAuthentication, cần gọi nó.
            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
                webSocketChatController.sendMessage(chatMessageRequest, headerAccessor);
            });
            assertTrue(exception.getMessage().contains("User not properly authenticated"));
            verify(chatService, never()).sendMessage(any(), any());
        }
    }

    @Nested
    @DisplayName("markMessagesAsRead Tests")
    class MarkMessagesAsReadTests {
        @Test
        @DisplayName("markMessagesAsRead - Valid User - Calls ChatService")
        void markMessagesAsRead_validUser_callsChatService() {
            doNothing().when(chatService).markMessagesAsRead(eq(mockAuthentication), eq(TEST_ROOM_ID));

            webSocketChatController.markMessagesAsRead(TEST_ROOM_ID, headerAccessor);

            verify(chatService).markMessagesAsRead(eq(mockAuthentication), eq(TEST_ROOM_ID));
        }
    }

    @Nested
    @DisplayName("Message Exception Handler Tests")
    class MessageExceptionHandlerTests {
        @Test
        @DisplayName("handleException - BadRequestException")
        void handleException_badRequestException() {
            BadRequestException ex = new BadRequestException("Invalid request data");
            Principal principal = headerAccessor.getUser();

            WebSocketErrorEvent errorEvent = webSocketChatController.handleException(ex, principal);

            assertNotNull(errorEvent);
            assertEquals("BadRequestException", errorEvent.getError());
            assertEquals("Invalid request data", errorEvent.getMessage());
        }

        @Test
        @DisplayName("handleException - ResourceNotFoundException")
        void handleException_resourceNotFoundException() {
            ResourceNotFoundException ex = new ResourceNotFoundException("ChatRoom", "id", 123L);
            Principal principal = headerAccessor.getUser();

            WebSocketErrorEvent errorEvent = webSocketChatController.handleException(ex, principal);

            assertEquals("ResourceNotFoundException", errorEvent.getError());
            assertEquals("ChatRoom not found with id : '123'", errorEvent.getMessage());
        }

        @Test
        @DisplayName("handleException - AccessDeniedException")
        void handleException_accessDeniedException() {
            AccessDeniedException ex = new AccessDeniedException("Not allowed");
            Principal principal = headerAccessor.getUser();

            WebSocketErrorEvent errorEvent = webSocketChatController.handleException(ex, principal);

            assertEquals("AccessDeniedException", errorEvent.getError());
            assertEquals("Permission denied.", errorEvent.getMessage()); // Message được tùy chỉnh
        }

        @Test
        @DisplayName("handleException - Generic Exception")
        void handleException_genericException() {
            RuntimeException ex = new RuntimeException("Some internal error");
            Principal principal = headerAccessor.getUser();

            WebSocketErrorEvent errorEvent = webSocketChatController.handleException(ex, principal);

            assertEquals("RuntimeException", errorEvent.getError());
            assertEquals("An error occurred while processing your request.", errorEvent.getMessage()); // Message được che giấu
        }

        @Test
        @DisplayName("handleException - Principal is Null")
        void handleException_principalIsNull() {
            RuntimeException ex = new RuntimeException("Error");

            WebSocketErrorEvent errorEvent = webSocketChatController.handleException(ex, null);

            assertEquals("RuntimeException", errorEvent.getError());
            assertEquals("An error occurred while processing your request.", errorEvent.getMessage());
            // Log sẽ ghi "Unknown User"
        }
    }

    @Nested
    @DisplayName("getAuthentication Helper Tests")
    class GetAuthenticationHelperTests {
        @Test
        @DisplayName("getAuthentication - Principal is Authentication - Returns Authentication")
        void getAuthentication_principalIsAuthentication_returnsAuthentication() {
            // headerAccessor đã được setup với mockAuthentication
            Authentication result = webSocketChatController.getAuthentication(headerAccessor);
            assertSame(mockAuthentication, result);
        }

        @Test
        @DisplayName("getAuthentication - Principal is Not Authentication - Throws AccessDeniedException")
        void getAuthentication_principalIsNotAuthentication_throwsAccessDeniedException() {
            headerAccessor.setUser(mock(Principal.class)); // Một Principal bất kỳ không phải Authentication
            assertThrows(AccessDeniedException.class,
                    () -> webSocketChatController.getAuthentication(headerAccessor));
        }

        @Test
        @DisplayName("getAuthentication - Principal is Null - Throws AccessDeniedException")
        void getAuthentication_principalIsNull_throwsAccessDeniedException() {
            headerAccessor.setUser(null);
            assertThrows(AccessDeniedException.class,
                    () -> webSocketChatController.getAuthentication(headerAccessor));
        }
    }
}