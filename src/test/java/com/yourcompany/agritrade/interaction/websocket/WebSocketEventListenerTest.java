package com.yourcompany.agritrade.interaction.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

  @Mock private SimpMessageSendingOperations messagingTemplate;
  @Mock private UserRepository userRepository;

  // Sử dụng @Spy cho các Map để có thể kiểm tra và reset trạng thái của chúng
  // Tuy nhiên, việc spy ConcurrentHashMap có thể phức tạp.
  // Cách đơn giản hơn là inject instance thật và reset thủ công.
  // Hoặc, nếu logic không quá phức tạp, có thể không cần @Spy.
  // Trong trường hợp này, chúng ta sẽ inject instance thật và reset.
  private Map<Long, Set<String>> userSessionsMap;
  private Map<Long, Boolean> onlineUsersStatusMap;

  @InjectMocks private WebSocketEventListener webSocketEventListener;

  private User testUser1, testUser2;
  private Authentication mockAuth1, mockAuth2;
  private final String SESSION_ID_1 = "session1";
  private final String SESSION_ID_2 = "session2";
  private final String SESSION_ID_3 = "session3";

  @BeforeEach
  void setUp() {
    testUser1 = new User();
    testUser1.setId(1L);
    testUser1.setEmail("user1@example.com");
    testUser1.setFullName("User One");

    testUser2 = new User();
    testUser2.setId(2L);
    testUser2.setEmail("user2@example.com");
    testUser2.setFullName("User Two");

    mockAuth1 =
        new UsernamePasswordAuthenticationToken(
            testUser1.getEmail(), null, Collections.emptyList());
    mockAuth2 =
        new UsernamePasswordAuthenticationToken(
            testUser2.getEmail(), null, Collections.emptyList());

    // Khởi tạo và inject các map vào listener
    userSessionsMap = new ConcurrentHashMap<>();
    onlineUsersStatusMap = new ConcurrentHashMap<>();
    // Sử dụng ReflectionTestUtils để inject các map này vào webSocketEventListener
    // vì chúng là final và được khởi tạo trong listener
    org.springframework.test.util.ReflectionTestUtils.setField(
        webSocketEventListener, "userSessions", userSessionsMap);
    org.springframework.test.util.ReflectionTestUtils.setField(
        webSocketEventListener, "onlineUsersStatus", onlineUsersStatusMap);

    lenient()
        .when(userRepository.findByEmail(testUser1.getEmail()))
        .thenReturn(Optional.of(testUser1));
    lenient()
        .when(userRepository.findByEmail(testUser2.getEmail()))
        .thenReturn(Optional.of(testUser2));
    lenient().when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
    lenient().when(userRepository.findById(testUser2.getId())).thenReturn(Optional.of(testUser2));
  }

  @AfterEach
  void tearDown() {
    // Xóa trạng thái của các map sau mỗi test
    userSessionsMap.clear();
    onlineUsersStatusMap.clear();
  }

  private SessionConnectEvent createConnectEvent(Principal principal, String sessionId) {
    SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
    headerAccessor.setUser(principal);
    headerAccessor.setSessionId(sessionId);
    Message<byte[]> message = new GenericMessage<>(new byte[0], headerAccessor.getMessageHeaders());
    return new SessionConnectEvent(this, message, principal);
  }

  private SessionDisconnectEvent createDisconnectEvent(
      Principal principal, String sessionId, String closeStatus) {
    SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
    headerAccessor.setUser(principal); // Principal thường là null khi disconnect
    headerAccessor.setSessionId(sessionId);
    Message<byte[]> message = new GenericMessage<>(new byte[0], headerAccessor.getMessageHeaders());
    // closeStatus không thực sự được dùng trong logic của bạn, nhưng cần cho constructor
    return new SessionDisconnectEvent(this, message, sessionId, null, principal);
  }

  @Nested
  @DisplayName("WebSocket Connect Listener Tests")
  class ConnectListenerTests {
    @Test
    @DisplayName("Handle Connect - New User Online")
    void handleWebSocketConnectListener_newUserOnline_shouldUpdateStatusAndBroadcast() {
      SessionConnectEvent event = createConnectEvent(mockAuth1, SESSION_ID_1);
      doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Map.class));

      webSocketEventListener.handleWebSocketConnectListener(event);

      assertTrue(userSessionsMap.containsKey(testUser1.getId()));
      assertTrue(userSessionsMap.get(testUser1.getId()).contains(SESSION_ID_1));
      assertTrue(onlineUsersStatusMap.getOrDefault(testUser1.getId(), false));

      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(messagingTemplate).convertAndSend(eq("/topic/presence"), payloadCaptor.capture());
      Map<String, Object> payload = payloadCaptor.getValue();
      assertEquals(testUser1.getId(), payload.get("userId"));
      assertEquals(testUser1.getEmail(), payload.get("username"));
      assertTrue((Boolean) payload.get("online"));
    }

    @Test
    @DisplayName(
        "Handle Connect - Existing User, New Session - Should Not Broadcast Again If Already Online")
    void handleWebSocketConnectListener_existingUserNewSession_shouldNotBroadcastIfAlreadyOnline() {
      // User 1 connects first session
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_1));
      reset(messagingTemplate); // Reset mock để chỉ kiểm tra lần gọi thứ hai

      // User 1 connects second session
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_2));

      assertEquals(2, userSessionsMap.get(testUser1.getId()).size());
      assertTrue(onlineUsersStatusMap.getOrDefault(testUser1.getId(), false));
      verify(messagingTemplate, never())
          .convertAndSend(eq("/topic/presence"), any(Map.class)); // Không broadcast lại
    }

    @Test
    @DisplayName("Handle Connect - User Not Found in DB - Should Log Error")
    void handleWebSocketConnectListener_userNotFoundInDb_shouldLogError() {
      when(userRepository.findByEmail(testUser1.getEmail())).thenReturn(Optional.empty());
      SessionConnectEvent event = createConnectEvent(mockAuth1, SESSION_ID_1);

      webSocketEventListener.handleWebSocketConnectListener(event);

      assertFalse(userSessionsMap.containsKey(testUser1.getId()));
      assertFalse(onlineUsersStatusMap.getOrDefault(testUser1.getId(), false));
      verify(messagingTemplate, never()).convertAndSend(anyString(), any(Map.class));
      // Kiểm tra log lỗi (khó trong unit test thuần túy)
    }

    @Test
    @DisplayName("Handle Connect - Anonymous User or No SessionId - Should Log Warn")
    void handleWebSocketConnectListener_anonymousOrNoSessionId_shouldLogWarn() {
      SessionConnectEvent eventNoPrincipal = createConnectEvent(null, SESSION_ID_1);
      webSocketEventListener.handleWebSocketConnectListener(eventNoPrincipal);

      SessionConnectEvent eventNoSessionId = createConnectEvent(mockAuth1, null);
      webSocketEventListener.handleWebSocketConnectListener(eventNoSessionId);

      assertTrue(userSessionsMap.isEmpty());
      assertTrue(onlineUsersStatusMap.isEmpty());
      verify(messagingTemplate, never()).convertAndSend(anyString(), any(Map.class));
    }
  }

  @Nested
  @DisplayName("WebSocket Disconnect Listener Tests")
  class DisconnectListenerTests {
    @Test
    @DisplayName("Handle Disconnect - Last Session of User - Should Mark Offline and Broadcast")
    void handleWebSocketDisconnectListener_lastSession_shouldMarkOfflineAndBroadcast() {
      // User 1 connects
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_1));
      reset(messagingTemplate); // Reset sau connect

      SessionDisconnectEvent disconnectEvent = createDisconnectEvent(null, SESSION_ID_1, "1000");
      doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Map.class));

      webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

      assertFalse(userSessionsMap.containsKey(testUser1.getId()));
      assertFalse(onlineUsersStatusMap.getOrDefault(testUser1.getId(), false));

      ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
      verify(messagingTemplate).convertAndSend(eq("/topic/presence"), payloadCaptor.capture());
      Map<String, Object> payload = payloadCaptor.getValue();
      assertEquals(testUser1.getId(), payload.get("userId"));
      assertEquals(testUser1.getEmail(), payload.get("username")); // getUsernameFromUserId được gọi
      assertFalse((Boolean) payload.get("online"));
    }

    @Test
    @DisplayName(
        "Handle Disconnect - User Has Other Sessions - Should Not Mark Offline or Broadcast")
    void handleWebSocketDisconnectListener_userHasOtherSessions_shouldNotMarkOffline() {
      // User 1 connects with two sessions
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_1));
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_2));
      reset(messagingTemplate);

      SessionDisconnectEvent disconnectEvent = createDisconnectEvent(null, SESSION_ID_1, "1000");
      webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

      assertTrue(userSessionsMap.containsKey(testUser1.getId()));
      assertEquals(1, userSessionsMap.get(testUser1.getId()).size());
      assertTrue(userSessionsMap.get(testUser1.getId()).contains(SESSION_ID_2));
      assertTrue(onlineUsersStatusMap.getOrDefault(testUser1.getId(), false)); // Vẫn online
      verify(messagingTemplate, never()).convertAndSend(eq("/topic/presence"), any(Map.class));
    }

    @Test
    @DisplayName("Handle Disconnect - SessionId Not Found - Should Log Warn")
    void handleWebSocketDisconnectListener_sessionIdNotFound_shouldLogWarn() {
      SessionDisconnectEvent disconnectEvent =
          createDisconnectEvent(null, "unknown_session", "1000");
      webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);
      verify(messagingTemplate, never()).convertAndSend(anyString(), any(Map.class));
    }
  }

  @Nested
  @DisplayName("Helper Method Tests")
  class HelperMethodTests {
    @Test
    @DisplayName("isUserOnline - Returns Correct Status")
    void isUserOnline_returnsCorrectStatus() {
      assertFalse(webSocketEventListener.isUserOnline(testUser1.getId()));
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_1));
      assertTrue(webSocketEventListener.isUserOnline(testUser1.getId()));
      webSocketEventListener.handleWebSocketDisconnectListener(
          createDisconnectEvent(null, SESSION_ID_1, "1000"));
      assertFalse(webSocketEventListener.isUserOnline(testUser1.getId()));
    }

    @Test
    @DisplayName("getOnlineUserIds - Returns Correct Set")
    void getOnlineUserIds_returnsCorrectSet() {
      assertTrue(webSocketEventListener.getOnlineUserIds().isEmpty());
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth1, SESSION_ID_1));
      webSocketEventListener.handleWebSocketConnectListener(
          createConnectEvent(mockAuth2, SESSION_ID_3)); // User 2 connects

      Set<Long> onlineIds = webSocketEventListener.getOnlineUserIds();
      assertEquals(2, onlineIds.size());
      assertTrue(onlineIds.contains(testUser1.getId()));
      assertTrue(onlineIds.contains(testUser2.getId()));

      webSocketEventListener.handleWebSocketDisconnectListener(
          createDisconnectEvent(null, SESSION_ID_1, "1000"));
      onlineIds = webSocketEventListener.getOnlineUserIds();
      assertEquals(1, onlineIds.size());
      assertFalse(onlineIds.contains(testUser1.getId()));
      assertTrue(onlineIds.contains(testUser2.getId()));
    }
  }

  // Test cho handleWebSocketSubscribeListener và handleWebSocketUnsubscribeListener
  // chủ yếu là để đảm bảo không có lỗi xảy ra, vì chúng chỉ log.
  @Test
  @DisplayName("Handle Subscribe Event - Does Not Throw")
  void handleWebSocketSubscribeListener_doesNotThrow() {
    SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
    headerAccessor.setUser(mockAuth1);
    headerAccessor.setDestination("/topic/someplace");
    Message<byte[]> message = new GenericMessage<>(new byte[0], headerAccessor.getMessageHeaders());
    SessionSubscribeEvent event = new SessionSubscribeEvent(this, message, mockAuth1);

    assertDoesNotThrow(() -> webSocketEventListener.handleWebSocketSubscribeListener(event));
  }

  @Test
  @DisplayName("Handle Unsubscribe Event - Does Not Throw")
  void handleWebSocketUnsubscribeListener_doesNotThrow() {
    SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
    headerAccessor.setUser(mockAuth1);
    headerAccessor.setSubscriptionId("sub1"); // Cần cho unsubscribe
    Message<byte[]> message = new GenericMessage<>(new byte[0], headerAccessor.getMessageHeaders());
    SessionUnsubscribeEvent event = new SessionUnsubscribeEvent(this, message, mockAuth1);

    assertDoesNotThrow(() -> webSocketEventListener.handleWebSocketUnsubscribeListener(event));
  }
}
