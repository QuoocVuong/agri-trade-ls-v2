package com.yourcompany.agritrade.interaction.websocket;

import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations; // Import để gửi message
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent; // Event khi kết nối
import org.springframework.web.socket.messaging.SessionDisconnectEvent; // Event khi ngắt kết nối
import org.springframework.web.socket.messaging.SessionSubscribeEvent; // Event khi subscribe
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent; // Event khi unsubscribe

import java.security.Principal; // Import Principal
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal userPrincipal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (userPrincipal != null && userPrincipal.getName() != null) {
            String username = userPrincipal.getName(); // Email
            // TODO: Lấy userId từ username (email) bằng cách query DB
            Long userId = getUserIdFromUsername(username); // Implement hàm này

            if (userId != null) {
                // Thêm session vào danh sách online
                onlineUsers.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
                log.info("WebSocket Connected: User -> {} (ID: {}), Session -> {}", username, userId, sessionId);
                log.info("Online users count: {}", onlineUsers.size());

                // TODO: Gửi thông báo user online đến bạn bè/followers
                // String destination = "/topic/presence.online";
                // messagingTemplate.convertAndSend(destination, Map.of("userId", userId, "username", username));
            } else {
                log.error("Could not find user ID for connected user: {}", username);
            }

        } else {
            log.warn("WebSocket Connected: Anonymous User, Session -> {}", sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal userPrincipal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (userPrincipal != null && userPrincipal.getName() != null) {
            String username = userPrincipal.getName();
            Long userId = getUserIdFromUsername(username);

            if (userId != null) {
                // Xóa session khỏi danh sách online
                Set<String> userSessions = onlineUsers.get(userId);
                if (userSessions != null) {
                    userSessions.remove(sessionId);
                    // Nếu user không còn session nào khác -> thực sự offline
                    if (userSessions.isEmpty()) {
                        onlineUsers.remove(userId);
                        log.info("User Offline: User -> {} (ID: {})", username, userId);
                        log.info("Online users count: {}", onlineUsers.size());
                        // TODO: Gửi thông báo user offline đến bạn bè/followers
                        // String destination = "/topic/presence.offline";
                        // messagingTemplate.convertAndSend(destination, Map.of("userId", userId, "username", username));
                    } else {
                        log.info("WebSocket Session Disconnected: User -> {} (ID: {}), Session -> {}. Still has other sessions.", username, userId, sessionId);
                    }
                }
            } else {
                log.error("Could not find user ID for disconnected user: {}", username);
            }
        } else {
            log.warn("WebSocket Disconnected: Anonymous User, Session -> {}", sessionId);
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

    private Long getUserIdFromUsername(String username) {
        if (username == null) return null;
        // Query DB để lấy ID từ email (username)
        return userRepository.findByEmail(username).map(User::getId).orElse(null);
    }

    // (Optional) Method để kiểm tra user có online không
    public boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }

    // (Optional) Method để lấy danh sách ID user đang online
    public Set<Long> getOnlineUserIds() {
        return onlineUsers.keySet();
    }

}