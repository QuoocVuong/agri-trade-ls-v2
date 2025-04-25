package com.yourcompany.agritrade.config;

import com.yourcompany.agritrade.config.websocket.AuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Bật tính năng message broker qua WebSocket (sử dụng STOMP)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired // Inject interceptor
    private AuthChannelInterceptor authChannelInterceptor;


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Tiền tố cho các đích đến mà client sẽ subscribe (ví dụ: /topic/public, /user/queue/private)
        // enableSimpleBroker sẽ tạo một message broker đơn giản trong bộ nhớ
        config.enableSimpleBroker("/topic", "/user"); // /user dùng cho private message
        // Tiền tố cho các đích đến mà client gửi message đến server (ví dụ: /app/chat.sendMessage)
        config.setApplicationDestinationPrefixes("/app");
        // Cấu hình để gửi tin nhắn riêng tư cho user theo session id
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint mà client sẽ kết nối WebSocket đến (thường dùng SockJS để fallback)
        // Cho phép các origin từ Angular dev server (hoặc *)
        registry.addEndpoint("/ws") // Đường dẫn kết nối WebSocket
                .setAllowedOrigins("http://localhost:4200") // URL của Angular dev server
                .withSockJS(); // Bật hỗ trợ SockJS fallback
        // Có thể thêm endpoint khác nếu cần
         //registry.addEndpoint("/ws-native").setAllowedOrigins("*"); // Endpoint không dùng SockJS
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:4200");

    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor); // Đăng ký interceptor
    }

    // Có thể override các phương thức khác để cấu hình thêm (interceptors, security...)
}