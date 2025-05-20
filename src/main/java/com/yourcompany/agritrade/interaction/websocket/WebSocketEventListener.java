package com.yourcompany.agritrade.interaction.websocket;

import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

  // Inject messagingTemplate nếu muốn gửi thông báo online/offline
  // private final SimpMessageSendingOperations messagingTemplate;
  private final SimpMessageSendingOperations messagingTemplate; // Để gửi thông báo online/offline

  // Lưu trạng thái online đơn giản (UserId -> Set<SessionId>)
  // Dùng ConcurrentHashMap để an toàn trong môi trường đa luồng
  private final Map<Long, Set<String>> onlineUsers = new ConcurrentHashMap<>();

  // *** INJECT USER REPOSITORY ***
  @Autowired // Hoặc inject qua constructor nếu bỏ @RequiredArgsConstructor
  private UserRepository userRepository;
  // ****************************

  // Map lưu trạng thái online (UserId -> isOnline) - Đơn giản hóa
  private final Map<Long, Boolean> onlineUsersStatus = new ConcurrentHashMap<>();

  // Map theo dõi session của user (UserId -> Set<SessionId>)
  private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

  @EventListener
  public void handleWebSocketConnectListener(SessionConnectEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    Principal userPrincipal = headerAccessor.getUser();
    String sessionId = headerAccessor.getSessionId();

    if (userPrincipal != null && userPrincipal.getName() != null && sessionId != null) {
      String username = userPrincipal.getName(); // Email
      Long userId = getUserIdFromUsername(username);

      if (userId != null) {
        // Thêm session vào danh sách theo dõi
        Set<String> sessions =
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        sessions.add(sessionId);

        // Kiểm tra xem user đã online trước đó chưa (từ session khác)
        boolean wasOffline = !onlineUsersStatus.getOrDefault(userId, false);

        // Đánh dấu user là online
        onlineUsersStatus.put(userId, true);

        log.info(
            "WebSocket Connected: User -> {} (ID: {}), Session -> {}. Total sessions for user: {}. Total online users: {}",
            username,
            userId,
            sessionId,
            sessions.size(),
            onlineUsersStatus.size());

        // Chỉ gửi broadcast ONLINE nếu user vừa chuyển từ offline sang online
        if (wasOffline) {
          log.info("User ONLINE: User -> {} (ID: {})", username, userId);
          broadcastPresenceStatus(userId, username, true);
        }
      } else {
        log.error("Could not find user ID for connected user: {}", username);
      }
    } else {
      log.warn(
          "WebSocket Connected: Anonymous User or missing session ID, Session -> {}", sessionId);
    }
  }

  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    // Principal thường là null ở disconnect, phải dựa vào sessionId
    String sessionId = headerAccessor.getSessionId();

    if (sessionId != null) {
      Long userIdToRemove = null;
      boolean userBecameOffline = false;

      // Tìm userId và xóa session khỏi map userSessions
      for (Map.Entry<Long, Set<String>> entry : userSessions.entrySet()) {
        if (entry.getValue().remove(sessionId)) { // Xóa session thành công
          userIdToRemove = entry.getKey();
          if (entry.getValue().isEmpty()) { // Nếu không còn session nào khác
            userSessions.remove(userIdToRemove); // Xóa user khỏi map session
            userBecameOffline = true; // Đánh dấu user đã offline
          }
          break; // Tìm thấy và xử lý xong
        }
      }

      if (userIdToRemove != null) {
        String username =
            getUsernameFromUserId(userIdToRemove); // Lấy lại username để log/broadcast
        // Chỉ cập nhật trạng thái và broadcast nếu user thực sự offline
        if (userBecameOffline) {
          onlineUsersStatus.remove(userIdToRemove); // Xóa khỏi map trạng thái online
          log.info(
              "User OFFLINE: User -> {} (ID: {}), Session -> {}. No more sessions. Total online users: {}",
              username,
              userIdToRemove,
              sessionId,
              onlineUsersStatus.size());
          broadcastPresenceStatus(userIdToRemove, username, false); // Gửi thông báo offline
        } else {
          log.info(
              "WebSocket Session Disconnected: User -> {} (ID: {}), Session -> {}. Still has other sessions online.",
              username,
              userIdToRemove,
              sessionId);
        }
      } else {
        log.warn(
            "Session {} disconnected but no associated user found in userSessions map.", sessionId);
      }
    } else {
      log.warn("WebSocket Disconnected: Missing session ID.");
    }
  }

  @EventListener
  public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    Principal userPrincipal = headerAccessor.getUser();
    String destination = headerAccessor.getDestination();
    if (userPrincipal != null) {
      log.debug("User {} subscribed to destination: {}", userPrincipal.getName(), destination);
    } else {
      log.debug("Anonymous user subscribed to destination: {}", destination);
    }
  }

  @EventListener
  public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    Principal userPrincipal = headerAccessor.getUser();
    String destination = headerAccessor.getDestination();
    if (userPrincipal != null) {
      log.debug("User {} unsubscribed from destination: {}", userPrincipal.getName(), destination);
    } else {
      log.debug("Anonymous user unsubscribed from destination: {}", destination);
    }
  }

  // --- Helper Method (Cần inject UserRepository) ---

  // Hàm helper để gửi thông báo trạng thái
  private void broadcastPresenceStatus(Long userId, String username, boolean isOnline) {
    String destination = "/topic/presence";
    Map<String, Object> payload = new HashMap<>(); // Dùng HashMap để dễ thêm bớt
    payload.put("userId", userId);
    payload.put("online", isOnline);
    payload.put("username", username); // Gửi thêm username nếu Frontend cần
    payload.put("timestamp", LocalDateTime.now().toString()); // Gửi timestamp dạng ISO string
    messagingTemplate.convertAndSend(destination, payload);
    log.info(
        "Broadcasted presence status for user {}: {}", userId, isOnline ? "ONLINE" : "OFFLINE");
  }

  private Long getUserIdFromUsername(String username) {
    if (username == null) return null;
    // Query DB để lấy ID từ email (username)
    return userRepository.findByEmail(username).map(User::getId).orElse(null);
  }

  // Hàm helper lấy username từ userId (để log khi disconnect)
  private String getUsernameFromUserId(Long userId) {
    if (userId == null) return "unknown";
    return userRepository.findById(userId).map(User::getEmail).orElse("unknown");
  }

  // Hàm để service khác kiểm tra trạng thái online
  public boolean isUserOnline(Long userId) {
    return onlineUsersStatus.getOrDefault(userId, false);
  }

  // Hàm lấy danh sách ID user đang online
  public Set<Long> getOnlineUserIds() {
    return onlineUsersStatus.entrySet().stream()
        .filter(Map.Entry::getValue)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }
}
