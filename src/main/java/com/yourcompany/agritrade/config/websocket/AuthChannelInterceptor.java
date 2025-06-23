package com.yourcompany.agritrade.config.websocket;

import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.config.security.UserDetailsServiceImpl;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthChannelInterceptor implements ChannelInterceptor {

  // Inject các bean cần thiết cho xác thực JWT
  private final JwtTokenProvider tokenProvider;
  private final UserDetailsServiceImpl userDetailsService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
      // Lấy token từ header 'Authorization' (hoặc header bạn dùng)
      String authHeader = accessor.getFirstNativeHeader("Authorization");
      log.debug("STOMP CONNECT attempt. Authorization header: {}", authHeader);

      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String jwt = authHeader.substring(7);
        try {
          if (tokenProvider.validateToken(jwt)) { // Hàm validate token của bạn
            String username = tokenProvider.getEmailFromToken(jwt); // Hàm lấy username từ token
            // Tạo đối tượng Authentication
            var userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            accessor.setUser(authentication);
            log.info("STOMP CONNECT authenticated for user: {}", username);
          } else {
            log.warn("STOMP CONNECT failed: Invalid JWT token provided.");
            // Không set user, Spring Security có thể sẽ từ chối sau đó
          }
        } catch (Exception e) {
          log.error("STOMP CONNECT failed: Error validating JWT token.", e);
          // Không set user
        }
      } else {
        log.warn("STOMP CONNECT failed: No valid Authorization header found.");
        // Không set user
      }
    } else if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
      Principal user = accessor.getUser();
      log.debug(
          "STOMP DISCONNECT received for user: {}", (user != null ? user.getName() : "unknown"));
    } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      Principal user = accessor.getUser();
      log.debug(
          "STOMP SUBSCRIBE received for user: {} to destination: {}",
          (user != null ? user.getName() : "anonymous"),
          accessor.getDestination());
      // Kiểm tra quyền subscribe
    } else if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
      Principal user = accessor.getUser();
      log.debug(
          "STOMP SEND received for user: {} to destination: {}",
          (user != null ? user.getName() : "anonymous"),
          accessor.getDestination());
    }

    return message;
  }
}
